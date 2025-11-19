package kr.hhplus.be.server.application

import kr.hhplus.be.server.api.dto.response.ConcertScheduleResponse
import kr.hhplus.be.server.api.dto.response.SeatResponse
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class ConcertScheduleFacade(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
    private val seatService: SeatService,
) {

    @Transactional(readOnly = true)
    fun getAvailableSchedules(concertId: Long): List<ConcertScheduleResponse> {
        val concert = concertService.findById(concertId)
        val availableSchedules = concertScheduleService.findByConcertId(concert.id)
            .filter { schedule -> schedule.isAvailable }

        return availableSchedules.map { ConcertScheduleResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getAvailableSeats(concertId: Long, scheduleId: Long): List<SeatResponse> {
        val concert = concertService.findById(concertId)
        val schedule = concertScheduleService.findById(scheduleId)
        schedule.validateIsConcert(concert)

        val availableSeats = seatService.findAllByConcertScheduleId(scheduleId)
            .filter { seat -> seat.isAvailable }

        return availableSeats.map { seat -> SeatResponse.from(seat) }
    }
}
