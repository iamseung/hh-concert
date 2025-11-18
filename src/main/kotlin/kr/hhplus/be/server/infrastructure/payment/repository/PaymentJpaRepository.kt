package kr.hhplus.be.server.infrastructure.payment.repository

import kr.hhplus.be.server.infrastructure.payment.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByReservationId(reservationId: Long): PaymentEntity?
    fun findAllByUserId(userId: Long): List<PaymentEntity>
}
