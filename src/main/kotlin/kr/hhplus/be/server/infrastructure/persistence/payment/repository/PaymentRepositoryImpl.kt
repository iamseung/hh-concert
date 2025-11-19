package kr.hhplus.be.server.infrastructure.persistence.payment.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.infrastructure.persistence.payment.entity.Payment
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import kr.hhplus.be.server.infrastructure.persistence.reservation.repository.ReservationJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : PaymentRepository {

    override fun save(paymentModel: PaymentModel): PaymentModel {
        val reservation = reservationJpaRepository.findByIdOrNull(paymentModel.reservationId)
            ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)

        val user = userJpaRepository.findByIdOrNull(paymentModel.userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val payment = Payment.fromDomain(paymentModel, reservation, user)
        val saved = paymentJpaRepository.save(payment)

        return saved.toModel()
    }

    override fun findById(id: Long): PaymentModel? {
        return paymentJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): PaymentModel {
        return findById(id) ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)
    }

    override fun findByReservationId(reservationId: Long): PaymentModel? {
        return paymentJpaRepository.findByReservationId(reservationId)?.toModel()
    }

    override fun findAllByUserId(userId: Long): List<PaymentModel> {
        return paymentJpaRepository.findAllByUserId(userId).map { it.toModel() }
    }
}
