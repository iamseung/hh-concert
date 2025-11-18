package kr.hhplus.be.server.domain.concert.repository

import kr.hhplus.be.server.domain.concert.model.ConcertSchedule
import java.time.LocalDate

interface ConcertScheduleRepository {
    fun save(concertSchedule: ConcertSchedule): ConcertSchedule
    fun findById(id: Long): ConcertSchedule?
    fun findByIdOrThrow(id: Long): ConcertSchedule
    fun findAllByConcertId(concertId: Long): List<ConcertSchedule>
    fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertSchedule>
}
