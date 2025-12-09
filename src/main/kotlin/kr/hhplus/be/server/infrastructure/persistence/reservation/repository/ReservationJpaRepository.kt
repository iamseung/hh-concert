package kr.hhplus.be.server.infrastructure.persistence.reservation.repository

import jakarta.persistence.LockModeType
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ReservationJpaRepository : JpaRepository<Reservation, Long> {
    fun findAllByUserId(userId: Long): List<Reservation>
    fun findByUserIdAndSeatId(userId: Long, seatId: Long): Reservation?
    fun findAllByReservationStatus(status: ReservationStatus): List<Reservation>

    @Query("SELECT r FROM Reservation r WHERE r.reservationStatus = 'TEMPORARY' AND r.temporaryExpiredAt < :now")
    fun findExpiredReservations(now: LocalDateTime): List<Reservation>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    fun findByIdWithLock(id: Long): Reservation?

    @Modifying
    @Query("SELECT r.seatId FROM Reservation r WHERE r.reservationStatus = 'TEMPORARY' AND r.temporaryExpiredAt < :now")
    fun findExpiredReservationSeatIds(now: LocalDateTime): List<Long>
}
