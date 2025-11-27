package kr.hhplus.be.server.domain.queue.repository

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel

interface QueueTokenRepository {
    fun save(queueTokenModel: QueueTokenModel): QueueTokenModel
    fun findById(id: Long): QueueTokenModel?
    fun findByToken(token: String): QueueTokenModel?
    fun findByTokenOrThrow(token: String): QueueTokenModel
    fun findAllByStatus(status: QueueStatus): List<QueueTokenModel>
    fun countByStatus(status: QueueStatus): Long
    fun findExpiredTokens(): List<QueueTokenModel>
}
