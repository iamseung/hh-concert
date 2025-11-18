package kr.hhplus.be.server.infrastructure.concert.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.concert.entity.SeatEntity

interface SeatJpaRepository : JpaRepository<SeatEntity, Long> {
    @EntityGraph(attributePaths = ["concertSchedule"])
    fun findByIdAndConcertScheduleId(id: Long, concertScheduleId: Long): SeatEntity?

    fun findAllByConcertScheduleId(concertScheduleId: Long): List<SeatEntity>

    fun findAllByConcertScheduleIdAndSeatStatus(concertScheduleId: Long, status: SeatStatus): List<SeatEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatEntity s WHERE s.id = :id")
    fun findByIdWithLock(id: Long): SeatEntity?
}
