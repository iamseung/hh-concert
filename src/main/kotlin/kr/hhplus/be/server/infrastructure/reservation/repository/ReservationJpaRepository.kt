package kr.hhplus.be.server.infrastructure.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.reservation.entity.ReservationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ReservationJpaRepository : JpaRepository<ReservationEntity, Long> {
    fun findAllByUserId(userId: Long): List<ReservationEntity>
    fun findByUserIdAndSeatId(userId: Long, seatId: Long): ReservationEntity?
    fun findAllByReservationStatus(status: ReservationStatus): List<ReservationEntity>

    @Query("SELECT r FROM ReservationEntity r WHERE r.reservationStatus = 'TEMPORARY' AND r.temporaryExpiredAt < :now")
    fun findExpiredReservations(now: LocalDateTime): List<ReservationEntity>
}
