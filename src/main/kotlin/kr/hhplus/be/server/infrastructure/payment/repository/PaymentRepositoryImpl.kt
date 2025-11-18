package kr.hhplus.be.server.infrastructure.payment.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.infrastructure.payment.entity.PaymentEntity
import kr.hhplus.be.server.infrastructure.reservation.repository.ReservationJpaRepository
import kr.hhplus.be.server.infrastructure.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = toEntity(payment)
        val saved = paymentJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): Payment {
        return findById(id) ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)
    }

    override fun findByReservationId(reservationId: Long): Payment? {
        return paymentJpaRepository.findByReservationId(reservationId)?.let { toDomain(it) }
    }

    override fun findAllByUserId(userId: Long): List<Payment> {
        return paymentJpaRepository.findAllByUserId(userId).map { toDomain(it) }
    }

    private fun toDomain(payment: PaymentEntity): Payment {
        return Payment.reconstitute(
            id = payment.id,
            reservationId = payment.reservationEntity.id,
            userId = payment.userEntity.id,
            amount = payment.amount,
            paymentStatus = payment.paymentStatus,
            paymentAt = payment.paymentAt,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
        )
    }

    private fun toEntity(payment: Payment): PaymentEntity {
        val reservation = reservationJpaRepository.findByIdOrNull(payment.reservationId)
            ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
        val user = userJpaRepository.findByIdOrNull(payment.userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val entity = PaymentEntity.of(reservation, user, payment.amount)

        return entity
    }
}