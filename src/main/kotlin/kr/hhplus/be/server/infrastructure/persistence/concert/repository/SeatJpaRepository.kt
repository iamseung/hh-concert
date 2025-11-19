package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import jakarta.persistence.LockModeType
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface SeatJpaRepository : JpaRepository<Seat, Long> {
    fun findAllByConcertScheduleId(concertScheduleId: Long): List<Seat>

    fun findAllByConcertScheduleIdAndSeatStatus(concertScheduleId: Long, status: SeatStatus): List<Seat>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    fun findByIdWithLock(id: Long): Seat?
}
