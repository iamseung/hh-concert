package kr.hhplus.be.server.domain.payment.service

import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun savePayment(paymentModel: PaymentModel): PaymentModel {
        return paymentRepository.save(paymentModel)
    }
}
