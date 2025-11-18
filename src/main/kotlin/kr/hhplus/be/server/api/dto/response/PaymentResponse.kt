package kr.hhplus.be.server.api.dto.response

import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.domain.payment.model.PaymentStatus

data class PaymentResponse(
    val paymentId: Long,
    val reservationId: Long,
    val userId: Long,
    val amount: Int,
    val paymentStatus: PaymentStatus,
) {
    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                paymentId = payment.id,
                reservationId = payment.reservationId,
                userId = payment.userId,
                amount = payment.amount,
                paymentStatus = payment.paymentStatus,
            )
        }
    }
}