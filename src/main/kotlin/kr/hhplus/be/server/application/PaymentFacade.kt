package kr.hhplus.be.server.application

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.api.dto.response.PaymentResponse
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
@Transactional
class PaymentFacade(
    private val userService: UserService,
    private val reservationService: ReservationService,
    private val seatService: SeatService,
    private val pointService: PointService,
    private val pointHistoryService: PointHistoryService,
    private val paymentService: PaymentService,
    private val queueTokenService: QueueTokenService,
) {

    fun processPayment(userId: Long, reservationId: Long, queueToken: String): PaymentResponse {
        userService.getUser(userId)
        val reservation = reservationService.findById(reservationId)

        reservation.validateOwnership(userId)
        reservation.validatePayable()

        val seat = seatService.findById(reservation.seatId)

        pointService.usePoint(userId, seat.price)
        pointHistoryService.savePointHistory(userId, seat.price, TransactionType.USE)

        val payment = paymentService.savePayment(Payment.create(reservationId, userId, seat.price))

        seat.confirmReservation()
        reservation.confirmPayment()

        val token = queueTokenService.getQueueTokenByToken(queueToken)
        queueTokenService.expireQueueToken(token)

        return PaymentResponse.from(payment)
    }
}