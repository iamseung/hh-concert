package kr.hhplus.be.server.infrastructure.persistence.point.repository

import jakarta.persistence.LockModeType
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PointJpaRepository : JpaRepository<Point, Long> {
    fun findByUserId(userId: Long): Point?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Point p WHERE p.user.id = :userId")
    fun findByUserIdWithLock(userId: Long): Point?
}
