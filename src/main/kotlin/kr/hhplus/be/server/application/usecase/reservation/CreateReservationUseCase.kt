package kr.hhplus.be.server.application.usecase.reservation

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import kr.hhplus.be.server.infrastructure.lock.DistributeLockExecutor
import kr.hhplus.be.server.infrastructure.template.TransactionExecutor
import org.springframework.stereotype.Component

@Component
class CreateReservationUseCase(
    private val userService: UserService,
    private val seatService: SeatService,
    private val reservationService: ReservationService,
    private val queueTokenService: QueueTokenService,
    private val concertScheduleService: ConcertScheduleService,
    private val distributeLockExecutor: DistributeLockExecutor,
    private val transactionExecutor: TransactionExecutor,
    private val seatCacheService: SeatCacheService,
) {

    /**
     * 좌석 예약 생성
     *
     * 동시성 제어 전략:
     * 1. 분산락으로 여러 서버 간 동일 좌석 예약 방지
     * 2. 트랜잭션으로 좌석 상태 변경, 예약 생성, 토큰 만료의 원자성 보장
     * 3. 트랜잭션 내부 토큰 재검증으로 TOCTOU 문제 방지
     *
     * 실행 순서:
     * [사전 검증] → [분산락 획득] → [트랜잭션 시작] → [토큰 재검증] → [좌석 예약 + 토큰 만료] → [트랜잭션 커밋] → [분산락 해제]
     *
     * 락 설정:
     * - Key: "seat:lock:{scheduleId}-{seatId}" - 좌석별 격리
     * - Wait: 3초 - 락 대기 시간
     * - Lease: 5초 - 자동 해제 시간 (데드락 방지)
     *
     * 트랜잭션 범위:
     * - 토큰 재검증 (ACTIVE 상태 확인)
     * - 좌석 상태 변경 (AVAILABLE → TEMPORARY_RESERVED)
     * - 예약 생성
     * - 대기열 토큰 만료 (ACTIVE → EXPIRED)
     */
    fun execute(command: CreateReservationCommand): CreateReservationResult {
        // 1. 대기열 토큰 사전 검증
        queueTokenService.getQueueTokenByToken(command.queueToken).validateActive()

        // 2. 사용자 검증
        val user = userService.findById(command.userId)

        // 3. 콘서트 일정 검증
        val schedule = concertScheduleService.findById(command.scheduleId)
        schedule.validateAvailable()

        // 4. 좌석 예약
        val reservation = distributeLockExecutor.execute(
            lockKey = "seat:lock:${command.scheduleId}-${command.seatId}",
            waitMilliSeconds = 3000,
            leaseMilliSeconds = 5000,
        ) {
            // 5. 트랜잭션 내부에서 좌석 예약, 예약 생성, 토큰 만료를 원자적으로 실행
            transactionExecutor.execute {
                // 토큰 재검증 (TOCTOU 방지: 락 대기 중 토큰이 만료되었을 수 있음)
                val token = queueTokenService.getQueueTokenByToken(command.queueToken)
                token.validateActive()

                // 좌석 임시 예약
                val seat = seatService.findByIdAndConcertScheduleId(command.seatId, schedule.id)
                seat.temporaryReservation()
                seatService.update(seat)

                // 예약 생성
                val newReservation = reservationService.save(ReservationModel.create(user.id, seat.id))

                // 토큰 만료 처리 (예약 완료 시 ACTIVE 자리 반납)
                queueTokenService.expireQueueToken(token)

                newReservation
            }
        }

        // 5. 좌석 캐시 무효화 (트랜잭션 커밋 후)
        // 좌석 상태가 변경되었으므로 해당 일정의 좌석 캐시를 삭제하여 다음 조회 시 최신 상태 반영
        seatCacheService.evictAvailableSeats(command.scheduleId)

        return CreateReservationResult(
            reservationId = reservation.id,
            userId = reservation.userId,
            seatId = reservation.seatId,
            status = reservation.reservationStatus,
            reservedAt = reservation.temporaryReservedAt,
        )
    }
}
