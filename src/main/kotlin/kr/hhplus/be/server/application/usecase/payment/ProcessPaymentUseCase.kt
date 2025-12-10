package kr.hhplus.be.server.application.usecase.payment

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProcessPaymentUseCase(
    private val userService: UserService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
    private val paymentService: PaymentService,
    private val queueTokenService: QueueTokenService,
) {

    @Transactional
    @Retryable(
        retryFor = [
            PessimisticLockingFailureException::class,
            CannotAcquireLockException::class,
        ],
        maxAttempts = 3,
        backoff = Backoff(delay = 150, multiplier = 2.0),
    )
    fun execute(command: ProcessPaymentCommand): ProcessPaymentResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 예약 검증 (비관적 락으로 중복 결제 방지)
        val reservation = reservationService.findByIdWithLock(command.reservationId)
        reservation.validate(user.id)

        // 3. 좌석 조회
        val seat = seatService.findById(reservation.seatId)

        // 4. 포인트 차감 및 히스토리 기록
        pointService.usePoint(user.id, seat.price)
        pointHistoryService.savePointHistory(user.id, seat.price, TransactionType.USE)

        // 5. 결제 저장
        val paymentModel = paymentService.savePayment(PaymentModel.create(command.reservationId, user.id, seat.price))

        // 6. 좌석 예약 확정
        seat.confirmReservation()
        seatService.update(seat)

        // 7. 예약 결제 완료 처리
        reservation.confirmPayment()
        reservationService.update(reservation)

        // 8. 대기열 토큰 만료
        val token = queueTokenService.getQueueTokenByToken(command.queueToken)
        queueTokenService.expireQueueToken(token)

        // 9. 결과 반환
        return ProcessPaymentResult(
            paymentId = paymentModel.id,
            reservationId = paymentModel.reservationId,
            userId = paymentModel.userId,
            amount = paymentModel.amount,
            paymentDate = paymentModel.paymentAt,
        )
    }
}
