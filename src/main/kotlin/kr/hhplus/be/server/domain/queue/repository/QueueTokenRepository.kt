package kr.hhplus.be.server.domain.queue.repository

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken

interface QueueTokenRepository {
    fun save(queueToken: QueueToken): QueueToken
    fun findById(id: Long): QueueToken?
    fun findByIdOrThrow(id: Long): QueueToken
    fun findByToken(token: String): QueueToken?
    fun findByTokenOrThrow(token: String): QueueToken
    fun findAllByStatus(status: QueueStatus): List<QueueToken>
    fun findTopWaitingTokens(limit: Int): List<QueueToken>
    fun countByStatus(status: QueueStatus): Long
    fun findExpiredTokens(): List<QueueToken>
}
