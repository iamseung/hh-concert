package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus

interface ReservationRepository {
    fun save(reservationModel: ReservationModel): ReservationModel
    fun findById(id: Long): ReservationModel?
    fun findByIdOrThrow(id: Long): ReservationModel
    fun findByUserIdAndSeatId(userId: Long, seatId: Long): ReservationModel?
    fun findAllByUserId(userId: Long): List<ReservationModel>
    fun findAllByStatus(status: ReservationStatus): List<ReservationModel>
    fun findExpiredReservations(): List<ReservationModel>
}
