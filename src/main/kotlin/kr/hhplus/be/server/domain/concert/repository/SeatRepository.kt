package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus

interface SeatRepository {
    fun findById(id: Long): SeatModel?
    fun findByIdOrThrow(id: Long): SeatModel
    fun findByIdWithLock(id: Long): SeatModel
    fun findAllByConcertScheduleId(concertScheduleId: Long): List<SeatModel>
    fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<SeatModel>
}
