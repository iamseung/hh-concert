package kr.hhplus.be.server.domain.reservation.service

import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import org.springframework.stereotype.Service

@Service
class ReservationService(
    private val reservationRepository: ReservationRepository,
) {

    fun findById(reservationId: Long): Reservation {
        return reservationRepository.findByIdOrThrow(reservationId)
    }

    fun findAllByUserId(userId: Long): List<Reservation> {
        return reservationRepository.findAllByUserId(userId)
    }

    fun save(reservation: Reservation): Reservation {
        return reservationRepository.save(reservation)
    }
}
