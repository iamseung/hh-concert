package kr.hhplus.be.server.infrastructure.persistence.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "idx_payment_reservation", columnList = "reservation_id", unique = true),
        Index(name = "idx_payment_user", columnList = "user_id, created_at")
    ]
)
class Payment(
    @Column(name = "reservation_id", nullable = false)
    val reservationId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    val amount: Int,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var paymentStatus: PaymentStatus = PaymentStatus.PENDING

    var paymentAt: LocalDateTime = LocalDateTime.now()

    fun toModel(): PaymentModel {
        return PaymentModel.reconstitute(
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

    fun updateFromDomain(paymentModel: PaymentModel) {
        this.paymentStatus = paymentModel.paymentStatus
        this.paymentAt = paymentModel.paymentAt
    }

    companion object {
        fun fromDomain(paymentModel: PaymentModel): Payment {
            return Payment(
                reservationId = paymentModel.reservationId,
                userId = paymentModel.userId,
                amount = paymentModel.amount,
            )
        }
    }
}
