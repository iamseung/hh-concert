package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.infrastructure.concert.entity.ConcertScheduleEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ConcertScheduleJpaRepository : JpaRepository<ConcertScheduleEntity, Long> {
    fun findByConcertId(concertId: Long): List<ConcertScheduleEntity>

    @EntityGraph(attributePaths = ["seats"])
    fun findByConcertIdAndId(concertId: Long, scheduleId: Long): ConcertScheduleEntity?

    @Query("SELECT cs FROM ConcertScheduleEntity cs WHERE cs.concertId = :concertId AND cs.concertDate >= :fromDate")
    fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertScheduleEntity>
}
