package kr.hhplus.be.server.application

import kr.hhplus.be.server.api.dto.response.ReservationResponse
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReservationFacade(
    private val userService: UserService,
    private val seatService: SeatService,
    private val reservationService: ReservationService,
    private val queueTokenService: QueueTokenService,
    private val concertScheduleService: ConcertScheduleService,
) {

    @Transactional(readOnly = true)
    fun getConcertReservations(userId: Long): List<ReservationResponse> {
        userService.findById(userId)
        val reservations = reservationService.findAllByUserId(userId)

        return reservations.map { ReservationResponse.from(it) }
    }

    @Transactional
    fun createReservation(
        userId: Long,
        scheduleId: Long,
        seatId: Long,
        queueToken: String,
    ): ReservationResponse {
        val token = queueTokenService.getQueueTokenByToken(queueToken)
        token.validateActive()

        userService.findById(userId)
        val schedule = concertScheduleService.findById(scheduleId)
        schedule.validateAvailable()

        val seat = seatService.findByIdAndConcertScheduleIdWithLock(seatId, schedule.id)
        seat.temporaryReservation()
        seatService.save(seat)

        val reservation = reservationService.save(Reservation.create(userId, seat.id))
        return ReservationResponse.from(reservation)
    }
}
