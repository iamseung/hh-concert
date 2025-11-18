package kr.hhplus.be.server.infrastructure.queue.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import kr.hhplus.be.server.infrastructure.queue.entity.QueueTokenEntity
import kr.hhplus.be.server.infrastructure.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class QueueTokenRepositoryImpl(
    private val queueTokenJpaRepository: QueueTokenJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : QueueTokenRepository {

    override fun save(queueToken: QueueToken): QueueToken {
        val entity = if (queueToken.id != 0L) {
            queueTokenJpaRepository.findByIdOrNull(queueToken.id)?.apply {
                updateFromDomain(queueToken)
            } ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(queueToken.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            QueueTokenEntity.fromDomain(queueToken, user)
        }
        val saved = queueTokenJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): QueueToken? {
        return queueTokenJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): QueueToken {
        return findById(id) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findByToken(token: String): QueueToken? {
        return queueTokenJpaRepository.findByToken(token)?.toDomain()
    }

    override fun findByTokenOrThrow(token: String): QueueToken {
        return findByToken(token) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findAllByStatus(status: QueueStatus): List<QueueToken> {
        return queueTokenJpaRepository.findAllByQueueStatusOrderByCreatedAtAsc(status).map { it.toDomain() }
    }

    override fun findTopWaitingTokens(limit: Int): List<QueueToken> {
        return queueTokenJpaRepository.findTopWaitingTokens(limit).map { it.toDomain() }
    }

    override fun countByStatus(status: QueueStatus): Long {
        return queueTokenJpaRepository.countByQueueStatus(status)
    }

    override fun findExpiredTokens(): List<QueueToken> {
        return queueTokenJpaRepository.findExpiredTokens(LocalDateTime.now()).map { it.toDomain() }
    }
}
