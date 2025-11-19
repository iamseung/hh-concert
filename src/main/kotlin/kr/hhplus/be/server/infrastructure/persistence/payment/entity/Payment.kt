package kr.hhplus.be.server.infrastructure.persistence.payment.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class Payment(
    @ManyToOne
    @JoinColumn(name = "reservation_id", nullable = false)
    val reservation: Reservation,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    val amount: Int,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var paymentStatus: PaymentStatus = PaymentStatus.PENDING

    var paymentAt: LocalDateTime = LocalDateTime.now()

    fun toModel(): PaymentModel {
        return PaymentModel.reconstitute(
            id = id,
            reservationId = reservation.id,
            userId = user.id,
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
        fun fromDomain(
            paymentModel: PaymentModel,
            reservation: Reservation,
            user: User,
        ): Payment {
            return Payment(
                reservation = reservation,
                user = user,
                amount = paymentModel.amount,
            )
        }
    }
}
