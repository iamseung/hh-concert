package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertJpaRepository : JpaRepository<Concert, Long>
