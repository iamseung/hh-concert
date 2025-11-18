package kr.hhplus.be.server.infrastructure.point.repository

import kr.hhplus.be.server.infrastructure.point.entity.PointHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryJpaRepository : JpaRepository<PointHistoryEntity, Long>
