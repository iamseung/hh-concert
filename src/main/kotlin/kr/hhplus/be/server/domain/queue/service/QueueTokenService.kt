package kr.hhplus.be.server.domain.queue.service

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.infrastructure.persistence.queue.repository.RedisQueueRepository
import org.springframework.stereotype.Service

@Service
class QueueTokenService(
    private val redisQueueRepository: RedisQueueRepository,
) {

    fun createQueueToken(userId: Long): QueueTokenModel {
        // 원자적으로 토큰 조회 또는 생성 (중복 토큰 방지)
        return redisQueueRepository.findOrCreateTokenAtomic(userId)
    }

    private fun createAndSaveToken(userId: Long, status: QueueStatus): QueueTokenModel {
        val queueTokenModel = QueueTokenModel.create(userId)
        if (status == QueueStatus.ACTIVE) {
            queueTokenModel.activate()
        }
        return redisQueueRepository.save(queueTokenModel)
    }

    fun getQueueTokenByToken(token: String): QueueTokenModel {
        return redisQueueRepository.findByTokenOrThrow(token)
    }

    /**
     * 대기 순번 조회 (WAITING 상태일 때만 의미 있음)
     */
    fun getQueuePosition(userId: Long): Long {
        return redisQueueRepository.getPosition(userId) ?: 0
    }

    fun expireQueueToken(queueTokenModel: QueueTokenModel): QueueTokenModel {
        queueTokenModel.expire()
        redisQueueRepository.removeFromActiveQueue(queueTokenModel.userId)
        redisQueueRepository.removeTokenMapping(queueTokenModel.token)  // 매핑 삭제 (메모리 누수 방지)
        return redisQueueRepository.update(queueTokenModel)
    }

    fun activateWaitingTokens(count: Int): Int {
        val activatedUserIds = redisQueueRepository.activateWaitingUsers(count)
        return activatedUserIds.size
    }

    fun cleanupExpiredTokens(): Int {
        val expiredUserIds = redisQueueRepository.removeExpiredActiveTokens()
        return expiredUserIds.size
    }
}
