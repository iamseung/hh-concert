package kr.hhplus.be.server.infrastructure.persistence.payment.repository

import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.infrastructure.persistence.payment.entity.Payment
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(paymentModel: PaymentModel): PaymentModel {
        val payment = Payment.fromDomain(paymentModel)
        val saved = paymentJpaRepository.save(payment)
        return saved.toModel()
    }

    override fun findById(id: Long): PaymentModel? {
        return paymentJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByReservationId(reservationId: Long): PaymentModel? {
        return paymentJpaRepository.findByReservationId(reservationId)?.toModel()
    }
}
