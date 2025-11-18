package kr.hhplus.be.server.domain.payment.service

import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.domain.payment.repository.PaymentRepository
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun savePayment(payment: Payment): Payment {
        return paymentRepository.save(payment)
    }
}
