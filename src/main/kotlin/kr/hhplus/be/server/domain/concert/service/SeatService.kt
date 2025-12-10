package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.repository.SeatRepository
import org.springframework.stereotype.Service

@Service
class SeatService(
    private val seatRepository: SeatRepository,
) {
    fun findByIdAndConcertScheduleIdWithLock(seatId: Long, scheduleId: Long): SeatModel {
        val seat = seatRepository.findByIdWithLock(seatId)
        seat.validateMatch(scheduleId)

        return seat
    }

    fun findById(seatId: Long): SeatModel {
        return seatRepository.findByIdOrThrow(seatId)
    }

    fun findAllByConcertScheduleId(scheduleId: Long): List<SeatModel> {
        return seatRepository.findAllByConcertScheduleId(scheduleId)
    }

    fun findAvailableSeatsByConcertScheduleId(scheduleId: Long): List<SeatModel> {
        return seatRepository.findAllByConcertScheduleIdAndStatus(scheduleId, kr.hhplus.be.server.domain.concert.model.SeatStatus.AVAILABLE)
    }

    fun update(seatModel: SeatModel): SeatModel {
        return seatRepository.update(seatModel)
    }

    fun restoreExpiredSeats(seatIds: List<Long>): Int {
        return seatRepository.bulkRestoreExpiredSeats(seatIds)
    }
}
