package kr.hhplus.be.server.domain.reservation.repository

import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus

interface ReservationRepository {
    fun save(reservationModel: ReservationModel): ReservationModel
    fun findById(id: Long): ReservationModel?
    fun findByIdOrThrow(id: Long): ReservationModel
    fun findAllByUserId(userId: Long): List<ReservationModel>
}
