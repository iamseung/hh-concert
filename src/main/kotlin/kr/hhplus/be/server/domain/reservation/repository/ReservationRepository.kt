package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import java.time.LocalDateTime

interface ReservationRepository {
    fun save(reservationModel: ReservationModel): ReservationModel
    fun update(reservationModel: ReservationModel): ReservationModel
    fun findById(id: Long): ReservationModel?
    fun findByIdOrThrow(id: Long): ReservationModel
    fun findByIdWithLock(id: Long): ReservationModel
    fun findAllByUserId(userId: Long): List<ReservationModel>
    fun findExpiredReservationSeatIds(now: LocalDateTime): List<Long>
}
