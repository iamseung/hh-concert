package kr.hhplus.be.server.infrastructure.persistence.queue.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import kr.hhplus.be.server.infrastructure.persistence.queue.entity.QueueToken
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class QueueTokenRepositoryImpl(
    private val queueTokenJpaRepository: QueueTokenJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : QueueTokenRepository {

    override fun save(queueTokenModel: QueueTokenModel): QueueTokenModel {
        val entity = if (queueTokenModel.id != 0L) {
            queueTokenJpaRepository.findByIdOrNull(queueTokenModel.id)?.apply {
                updateFromDomain(queueTokenModel)
            } ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(queueTokenModel.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            QueueToken.fromDomain(queueTokenModel, user)
        }
        val saved = queueTokenJpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): QueueTokenModel? {
        return queueTokenJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): QueueTokenModel {
        return findById(id) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findByToken(token: String): QueueTokenModel? {
        return queueTokenJpaRepository.findByToken(token)?.toModel()
    }

    override fun findByTokenOrThrow(token: String): QueueTokenModel {
        return findByToken(token) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findAllByStatus(status: QueueStatus): List<QueueTokenModel> {
        return queueTokenJpaRepository.findAllByQueueStatusOrderByCreatedAtAsc(status).map { it.toModel() }
    }

    override fun findTopWaitingTokens(limit: Int): List<QueueTokenModel> {
        return queueTokenJpaRepository.findTopWaitingTokens(limit).map { it.toModel() }
    }

    override fun countByStatus(status: QueueStatus): Long {
        return queueTokenJpaRepository.countByQueueStatus(status)
    }

    override fun findExpiredTokens(): List<QueueTokenModel> {
        return queueTokenJpaRepository.findExpiredTokens(LocalDateTime.now()).map { it.toModel() }
    }
}
