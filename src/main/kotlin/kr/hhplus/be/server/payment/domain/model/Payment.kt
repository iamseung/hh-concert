package kr.hhplus.be.server.payment.domain.model

import java.time.LocalDateTime

class Payment private constructor(
    private var id: Long?,
    val reservationId: Long,
    val userId: Long,
    val amount: Int,
    val paymentStatus: PaymentStatus,
    val paymentAt: LocalDateTime,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun assignId(id: Long) {
        this.id = id
    }

    fun getId(): Long? = id
    fun getReservationId(): Long = reservationId
    fun getUserId(): Long = userId
    fun getAmount(): Int = amount
    fun getPaymentStatus(): PaymentStatus = paymentStatus
    fun getPaymentAt(): LocalDateTime = paymentAt
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt

    companion object {
        fun create(reservationId: Long, userId: Long, amount: Int): Payment {
            val now = LocalDateTime.now()
            return Payment(
                id = null,
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
        ): Payment {
            return Payment(
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
