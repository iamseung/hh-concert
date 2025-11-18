package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus

interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
    fun findById(id: Long): Reservation?
    fun findByIdOrThrow(id: Long): Reservation
    fun findByUserIdAndSeatId(userId: Long, seatId: Long): Reservation?
    fun findAllByUserId(userId: Long): List<Reservation>
    fun findAllByStatus(status: ReservationStatus): List<Reservation>
    fun findExpiredReservations(): List<Reservation>
}
