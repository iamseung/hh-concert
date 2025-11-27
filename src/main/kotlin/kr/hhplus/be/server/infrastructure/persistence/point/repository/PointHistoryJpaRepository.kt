package kr.hhplus.be.server.infrastructure.persistence.point.repository

import kr.hhplus.be.server.infrastructure.persistence.point.entity.PointHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointHistoryJpaRepository : JpaRepository<PointHistory, Long>
