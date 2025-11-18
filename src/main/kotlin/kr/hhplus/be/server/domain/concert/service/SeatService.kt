package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
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

    fun findByIdAndConcertScheduleIdWithLock(seatId: Long, scheduleId: Long): Seat {
        val seat = seatRepository.findByIdWithLock(seatId)
            ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
        if (seat.concertScheduleId != scheduleId) {
            throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
        }
        return seat
    }

    fun findById(seatId: Long): Seat {
        return seatRepository.findByIdOrThrow(seatId)
    }

    fun findAllByConcertScheduleId(scheduleId: Long): List<Seat> {
        return seatRepository.findAllByConcertScheduleId(scheduleId)
    }

    fun save(seat: Seat): Seat {
        return seatRepository.save(seat)
    }
}
