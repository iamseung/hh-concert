package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import org.springframework.stereotype.Service

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
) {

    fun findById(reservationId: Long): ReservationModel {
        return reservationRepository.findByIdOrThrow(reservationId)
    }

    fun findAllByUserId(userId: Long): List<ReservationModel> {
        return reservationRepository.findAllByUserId(userId)
    }

    fun save(reservationModel: ReservationModel): ReservationModel {
        return reservationRepository.save(reservationModel)
    }
}
