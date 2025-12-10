package kr.hhplus.be.server.application.usecase.reservation

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateReservationUseCase(
    private val userService: UserService,
    private val seatService: SeatService,
    private val reservationService: ReservationService,
    private val queueTokenService: QueueTokenService,
    private val concertScheduleService: ConcertScheduleService,
) {

    @Transactional
    @Retryable(
        retryFor = [
            PessimisticLockingFailureException::class,
            CannotAcquireLockException::class,
        ],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun execute(command: CreateReservationCommand): CreateReservationResult {
        // 1. 대기열 토큰 검증
        val token = queueTokenService.getQueueTokenByToken(command.queueToken)
        token.validateActive()

        // 2. 사용자 검증
        val user = userService.findById(command.userId)

        // 3. 콘서트 일정 검증
        val schedule = concertScheduleService.findById(command.scheduleId)
        schedule.validateAvailable()

        // 4. 좌석 조회 및 임시 예약
        val seat = seatService.findByIdAndConcertScheduleIdWithLock(command.seatId, schedule.id)
        seat.temporaryReservation()
        seatService.update(seat)

        // 5. 예약 생성
        val reservation = reservationService.save(ReservationModel.create(user.id, seat.id))

        // 6. 토큰 만료 처리 (예약 완료 시 ACTIVE 자리 반납)
        queueTokenService.expireQueueToken(token)

        return CreateReservationResult(
            reservationId = reservation.id,
            userId = reservation.userId,
            seatId = reservation.seatId,
            status = reservation.reservationStatus,
            reservedAt = reservation.temporaryReservedAt,
        )
    }
}
