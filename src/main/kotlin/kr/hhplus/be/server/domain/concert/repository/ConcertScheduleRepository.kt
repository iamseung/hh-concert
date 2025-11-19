package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import java.time.LocalDate

interface ConcertScheduleRepository {
    fun save(concertScheduleModel: ConcertScheduleModel): ConcertScheduleModel
    fun findById(id: Long): ConcertScheduleModel?
    fun findByIdOrThrow(id: Long): ConcertScheduleModel
    fun findAllByConcertId(concertId: Long): List<ConcertScheduleModel>
    fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertScheduleModel>
}
