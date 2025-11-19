package kr.hhplus.be.server.infrastructure.persistence.payment.repository

import kr.hhplus.be.server.infrastructure.persistence.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByReservationId(reservationId: Long): Payment?
    fun findAllByUserId(userId: Long): List<Payment>
}
