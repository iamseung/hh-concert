package kr.hhplus.be.server.infrastructure.persistence.queue.repository

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.infrastructure.persistence.queue.entity.QueueToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface QueueTokenJpaRepository : JpaRepository<QueueToken, Long> {
    fun findByToken(token: String): QueueToken?

    fun findByUserIdAndQueueStatus(userId: Long, queueStatus: QueueStatus): QueueToken?

    fun countByQueueStatus(queueStatus: QueueStatus): Long

    fun findAllByQueueStatusOrderByCreatedAtAsc(queueStatus: QueueStatus): List<QueueToken>

    @Query("SELECT q FROM QueueToken q WHERE q.queueStatus = 'WAITING' ORDER BY q.createdAt ASC LIMIT :limit")
    fun findTopWaitingTokens(limit: Int): List<QueueToken>

    @Query("SELECT q FROM QueueToken q WHERE q.queueStatus = 'ACTIVE' AND q.expiresAt < :now")
    fun findExpiredTokens(now: LocalDateTime): List<QueueToken>

    @Query("SELECT COUNT(q) FROM QueueToken q WHERE q.queueStatus = 'WAITING' AND q.queuePosition < :position")
    fun countWaitingTokensBeforePosition(position: Int): Long
}
