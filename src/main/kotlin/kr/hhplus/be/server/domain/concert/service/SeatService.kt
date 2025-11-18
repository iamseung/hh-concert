package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.repository.SeatRepository
import org.springframework.stereotype.Service

@Service
class SeatService(
    private val seatRepository: SeatRepository,
) {
    fun findByIdAndConcertScheduleId(seatId: Long, scheduleId: Long): Seat {
        return seatRepository.findByIdOrThrow(seatId)
    }

    fun findById(seatId: Long): Seat {
        return seatRepository.findByIdOrThrow(seatId)
    }

    fun findAllByConcertScheduleId(scheduleId: Long): List<Seat> {
        return seatRepository.findAllByConcertScheduleId(scheduleId)
    }
}
