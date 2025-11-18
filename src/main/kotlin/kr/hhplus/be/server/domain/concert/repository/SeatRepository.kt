package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.model.SeatStatus

interface SeatRepository {
    fun save(seat: Seat): Seat
    fun findById(id: Long): Seat?
    fun findByIdOrThrow(id: Long): Seat
    fun findByIdWithLock(id: Long): Seat?
    fun findAllByConcertScheduleId(concertScheduleId: Long): List<Seat>
    fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<Seat>
}
