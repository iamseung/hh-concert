package kr.hhplus.be.server.infrastructure.payment.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.reservation.entity.ReservationEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class PaymentEntity(
    @ManyToOne
    @JoinColumn(name = "reservation_id", nullable = false)
    val reservationEntity: ReservationEntity,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val userEntity: UserEntity,

    val amount: Int,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var paymentStatus: PaymentStatus = PaymentStatus.PENDING

    var paymentAt: LocalDateTime = LocalDateTime.now()

    fun toDomain(): Payment {
        return Payment.reconstitute(
            id = id,
            reservationId = reservationEntity.id,
            userId = userEntity.id,
            amount = amount,
            paymentStatus = paymentStatus,
            paymentAt = paymentAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(payment: Payment) {
        this.paymentStatus = payment.paymentStatus
        this.paymentAt = payment.paymentAt
    }

    companion object {
        fun fromDomain(
            payment: Payment,
            reservationEntity: ReservationEntity,
            userEntity: UserEntity,
        ): PaymentEntity {
            return PaymentEntity(
                reservationEntity = reservationEntity,
                userEntity = userEntity,
                amount = payment.amount,
            )
        }
    }
}
