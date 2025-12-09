package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import java.time.LocalDateTime

interface SeatRepository {
    fun update(seatModel: SeatModel): SeatModel
    fun findById(id: Long): SeatModel?
    fun findByIdOrThrow(id: Long): SeatModel
    fun findByIdWithLock(id: Long): SeatModel
    fun findAllByConcertScheduleId(concertScheduleId: Long): List<SeatModel>
    fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<SeatModel>
    fun findAllByStatus(status: SeatStatus): List<SeatModel>
    fun bulkRestoreExpiredSeats(seatIds: List<Long>): Int
}
