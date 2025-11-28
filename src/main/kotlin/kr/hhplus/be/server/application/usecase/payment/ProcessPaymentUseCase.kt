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
    fun execute(command: ProcessPaymentCommand): ProcessPaymentResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 예약 검증
        val reservation = reservationService.findById(command.reservationId)
        reservation.validateOwnership(user.id)
        reservation.validatePayable()

        // 3. 좌석 조회
        val seat = seatService.findById(reservation.seatId)

        // 4. 포인트 차감 및 히스토리 기록
        pointService.usePoint(user.id, seat.price)
        pointHistoryService.savePointHistory(user.id, seat.price, TransactionType.USE)

        // 5. 결제 저장
        val paymentModel = paymentService.savePayment(PaymentModel.create(command.reservationId, user.id, seat.price))

        // 6. 좌석 예약 확정
        seat.confirmReservation()

        // 7. 예약 결제 완료 처리
        reservation.confirmPayment()
        reservationService.save(reservation)

        // 8. 대기열 토큰 만료
        val token = queueTokenService.getQueueTokenByToken(command.queueToken)
        queueTokenService.expireQueueToken(token)

        // 9. 결과 반환
        return ProcessPaymentResult(
            paymentId = paymentModel.id,
            reservationId = paymentModel.reservationId,
            userId = paymentModel.userId,
            amount = paymentModel.amount,
            paymentDate = paymentModel.paymentAt
        )
    }
}
