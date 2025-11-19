package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ConcertScheduleJpaRepository : JpaRepository<ConcertSchedule, Long> {
    fun findByConcertId(concertId: Long): List<ConcertSchedule>

    @EntityGraph(attributePaths = ["seats"])
    fun findByConcertIdAndId(concertId: Long, scheduleId: Long): ConcertSchedule?

    @Query("SELECT cs FROM ConcertSchedule cs WHERE cs.concertId = :concertId AND cs.concertDate >= :fromDate")
    fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertSchedule>
}
