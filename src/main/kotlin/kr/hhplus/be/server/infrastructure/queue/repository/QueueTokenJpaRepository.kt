package kr.hhplus.be.server.infrastructure.queue.repository

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.infrastructure.queue.entity.QueueTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface QueueTokenJpaRepository : JpaRepository<QueueTokenEntity, Long> {
    fun findByToken(token: String): QueueTokenEntity?

    fun findByUserIdAndQueueStatus(userId: Long, queueStatus: QueueStatus): QueueTokenEntity?

    fun countByQueueStatus(queueStatus: QueueStatus): Long

    fun findAllByQueueStatusOrderByCreatedAtAsc(queueStatus: QueueStatus): List<QueueTokenEntity>

    @Query("SELECT q FROM QueueTokenEntity q WHERE q.queueStatus = 'WAITING' ORDER BY q.createdAt ASC LIMIT :limit")
    fun findTopWaitingTokens(limit: Int): List<QueueTokenEntity>

    @Query("SELECT q FROM QueueTokenEntity q WHERE q.queueStatus = 'ACTIVE' AND q.expiresAt < :now")
    fun findExpiredTokens(now: LocalDateTime): List<QueueTokenEntity>

    @Query("SELECT COUNT(q) FROM QueueTokenEntity q WHERE q.queueStatus = 'WAITING' AND q.queuePosition < :position")
    fun countWaitingTokensBeforePosition(position: Int): Long
}
