package kr.hhplus.be.server.infrastructure.persistence.payment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
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
