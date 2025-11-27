package kr.hhplus.be.server.api.dto.response

import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.model.PaymentStatus

data class PaymentResponse(
    val paymentId: Long,
    val reservationId: Long,
    val userId: Long,
    val amount: Int,
    val paymentStatus: PaymentStatus,
) {
    companion object {
        fun from(paymentModel: PaymentModel): PaymentResponse {
            return PaymentResponse(
                paymentId = paymentModel.id,
                reservationId = paymentModel.reservationId,
                userId = paymentModel.userId,
                amount = paymentModel.amount,
                paymentStatus = paymentModel.paymentStatus,
            )
        }
    }
}
