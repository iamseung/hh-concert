package kr.hhplus.be.server.domain.payment.model

import java.time.LocalDateTime

class PaymentModel private constructor(
    var id: Long,
    val reservationId: Long,
    val userId: Long,
    val amount: Int,
    val paymentStatus: PaymentStatus,
    val paymentAt: LocalDateTime,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    companion object {
        fun create(reservationId: Long, userId: Long, amount: Int): PaymentModel {
            val now = LocalDateTime.now()
            return PaymentModel(
                id = 0L,
                reservationId = reservationId,
                userId = userId,
                amount = amount,
                paymentStatus = PaymentStatus.PENDING,
                paymentAt = now,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            reservationId: Long,
            userId: Long,
            amount: Int,
            paymentStatus: PaymentStatus,
            paymentAt: LocalDateTime,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): PaymentModel {
            return PaymentModel(
                id = id,
                reservationId = reservationId,
                userId = userId,
                amount = amount,
                paymentStatus = paymentStatus,
                paymentAt = paymentAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
