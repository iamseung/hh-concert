package kr.hhplus.be.server.infrastructure.point.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import kr.hhplus.be.server.infrastructure.point.entity.PointEntity

interface PointJpaRepository : JpaRepository<PointEntity, Long> {
    fun findByUserId(userId: Long): PointEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointEntity p WHERE p.userEntity.id = :userId")
    fun findByUserIdWithLock(userId: Long): PointEntity?
}
