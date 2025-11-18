package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.infrastructure.concert.entity.ConcertEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertJpaRepository : JpaRepository<ConcertEntity, Long>
