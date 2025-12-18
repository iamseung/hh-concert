package kr.hhplus.be.server.application.usecase.payment

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import kr.hhplus.be.server.infrastructure.lock.DistributeLockExecutor
import kr.hhplus.be.server.infrastructure.template.TransactionExecutor
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ProcessPaymentUseCase(
    private val userService: UserService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val concertScheduleService: ConcertScheduleService,
    private val concertService: ConcertService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
    private val paymentService: PaymentService,
    private val queueTokenService: QueueTokenService,
    private val distributeLockExecutor: DistributeLockExecutor,
    private val transactionExecutor: TransactionExecutor,
    private val seatCacheService: SeatCacheService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    /**
     * 결제 처리
     *
     * 동시성 제어 전략:
     * 1. 분산락으로 여러 서버 간 동일 예약에 대한 중복 결제 방지
     * 2. 트랜잭션으로 포인트 차감, 결제 생성, 예약/좌석 상태 변경, 토큰 만료의 원자성 보장
     * 3. 트랜잭션 내부 예약 재검증으로 TOCTOU 문제 방지
     *
     * 실행 순서:
     * [사전 검증] → [분산락 획득] → [트랜잭션 시작] → [재검증 + 결제 처리] → [트랜잭션 커밋] → [분산락 해제]
     *
     * 락 설정:
     * - Key: "reservation:payment:lock:{reservationId}" - 예약별 격리
     * - Wait: 3초 - 락 대기 시간
     * - Lease: 5초 - 자동 해제 시간 (데드락 방지)
     *
     * 트랜잭션 범위 (WRITE 작업만):
     * - 예약 재검증 (TOCTOU 방지)
     * - 포인트 차감 및 히스토리 기록
     * - 결제 생성
     * - 좌석 상태 변경 (TEMPORARY_RESERVED → RESERVED)
     * - 예약 상태 변경 (TEMPORARY_RESERVED → CONFIRMED)
     * - 대기열 토큰 만료 (ACTIVE → EXPIRED)
     */
    fun execute(command: ProcessPaymentCommand): ProcessPaymentResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 예약 사전 검증
        val reservationPreCheck = reservationService.findById(command.reservationId)
        reservationPreCheck.validate(user.id)

        // 3. 좌석 조회
        val seat = seatService.findById(reservationPreCheck.seatId)

        // 4. 분산락으로 보호되는 결제 처리
        val payment = distributeLockExecutor.execute(
            lockKey = "reservation:payment:lock:${command.reservationId}",
            waitMilliSeconds = 3000,
            leaseMilliSeconds = 5000,
        ) {
            // 5. 트랜잭션 내부에서 결제 처리를 원자적으로 실행
            transactionExecutor.execute {
                // 예약 재검증 (TOCTOU 방지: 락 대기 중 예약 상태가 변경되었을 수 있음)
                val reservation = reservationService.findById(command.reservationId)
                reservation.validate(user.id)

                // 포인트 차감 및 히스토리 기록
                pointService.usePoint(user.id, seat.price)
                pointHistoryService.savePointHistory(user.id, seat.price, TransactionType.USE)

                // 결제 생성
                val paymentModel = paymentService.savePayment(
                    PaymentModel.create(command.reservationId, user.id, seat.price),
                )

                // 좌석 예약 확정
                seat.confirmReservation()
                seatService.update(seat)

                // 예약 결제 완료 처리
                reservation.confirmPayment()
                reservationService.update(reservation)

                // 대기열 토큰 만료
                val token = queueTokenService.getQueueTokenByToken(command.queueToken)
                queueTokenService.expireQueueToken(token)

                paymentModel
            }
        }

        // 5. 좌석 캐시 무효화 (트랜잭션 커밋 후)
        // 좌석 상태가 TEMPORARY_RESERVED → RESERVED로 변경되었으므로 캐시 삭제
        seatCacheService.evictAvailableSeats(seat.concertScheduleId)

        // 6. 랭킹 업데이트 이벤트 발행 (트랜잭션 커밋 후 비동기 처리)
        val schedule = concertScheduleService.findById(seat.concertScheduleId)
        val concert = concertService.findById(schedule.concertId)
        eventPublisher.publishEvent(
            ReservationConfirmedEvent(
                reservationId = payment.reservationId,
                concertId = concert.id,
                concertTitle = concert.title,
                userId = payment.userId,
            ),
        )

        // 7. 결과 반환
        return ProcessPaymentResult(
            paymentId = payment.id,
            reservationId = payment.reservationId,
            userId = payment.userId,
            amount = payment.amount,
            paymentDate = payment.paymentAt,
        )
    }
}
