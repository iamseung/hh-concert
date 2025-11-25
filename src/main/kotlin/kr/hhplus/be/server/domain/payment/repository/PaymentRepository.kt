package kr.hhplus.be.server.domain.payment.repository

import kr.hhplus.be.server.domain.payment.model.PaymentModel

interface PaymentRepository {
    fun save(paymentModel: PaymentModel): PaymentModel
    fun findById(id: Long): PaymentModel?
    fun findByReservationId(reservationId: Long): PaymentModel?
}
