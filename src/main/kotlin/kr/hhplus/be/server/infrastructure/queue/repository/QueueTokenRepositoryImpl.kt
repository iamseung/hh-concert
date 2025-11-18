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
        val entity = toEntity(queueToken)
        val saved = queueTokenJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): QueueToken? {
        return queueTokenJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): QueueToken {
        return findById(id) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findByToken(token: String): QueueToken? {
        return queueTokenJpaRepository.findByToken(token)?.let { toDomain(it) }
    }

    override fun findByTokenOrThrow(token: String): QueueToken {
        return findByToken(token) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findAllByStatus(status: QueueStatus): List<QueueToken> {
        return queueTokenJpaRepository.findAllByQueueStatusOrderByCreatedAtAsc(status).map { toDomain(it) }
    }

    override fun findTopWaitingTokens(limit: Int): List<QueueToken> {
        return queueTokenJpaRepository.findTopWaitingTokens(limit).map { toDomain(it) }
    }

    override fun countByStatus(status: QueueStatus): Long {
        return queueTokenJpaRepository.countByQueueStatus(status)
    }

    override fun findExpiredTokens(): List<QueueToken> {
        return queueTokenJpaRepository.findExpiredTokens(LocalDateTime.now()).map { toDomain(it) }
    }

    private fun toDomain(queueToken: QueueTokenEntity): QueueToken {
        return QueueToken.reconstitute(
            id = queueToken.id,
            userId = queueToken.userEntity.id,
            token = queueToken.token,
            queueStatus = queueToken.queueStatus,
            queuePosition = queueToken.queuePosition,
            activatedAt = queueToken.activatedAt,
            expiresAt = queueToken.expiresAt,
            createdAt = queueToken.createdAt,
            updatedAt = queueToken.updatedAt,
        )
    }

    private fun toEntity(queueToken: QueueToken): QueueTokenEntity {
        val user = userJpaRepository.findByIdOrNull(queueToken.userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

        val entity = QueueTokenEntity.of(user, queueToken.queuePosition)
        entity.queueStatus = queueToken.queueStatus
        queueToken.activatedAt?.let { entity.activatedAt = it }
        queueToken.expiresAt?.let { entity.expiresAt = it }
        return entity
    }
}