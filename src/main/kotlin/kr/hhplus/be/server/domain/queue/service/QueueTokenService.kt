package kr.hhplus.be.server.domain.queue.service

import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import org.springframework.stereotype.Service

@Service
class QueueTokenService(
    private val queueTokenRepository: QueueTokenRepository,
) {

    fun createQueueToken(userId: Long): QueueTokenModel {
        // 원자적으로 토큰 조회 또는 생성 (중복 토큰 방지)
        return queueTokenRepository.findOrCreateTokenAtomic(userId)
    }

    fun getQueueTokenByToken(token: String): QueueTokenModel {
        return queueTokenRepository.findByTokenOrThrow(token)
    }

    /**
     * 대기 순번 조회 (WAITING 상태일 때만 의미 있음)
     */
    fun getQueuePosition(userId: Long): Long {
        return queueTokenRepository.getPosition(userId) ?: 0
    }

    fun expireQueueToken(queueTokenModel: QueueTokenModel): QueueTokenModel {
        queueTokenModel.expire()
        queueTokenRepository.removeFromActiveQueue(queueTokenModel.userId)
        queueTokenRepository.removeTokenMapping(queueTokenModel.token) // 매핑 삭제 (메모리 누수 방지)
        return queueTokenRepository.update(queueTokenModel)
    }

    fun activateWaitingTokens(count: Int): Int {
        val activatedUserIds = queueTokenRepository.activateWaitingUsers(count)
        return activatedUserIds.size
    }

    fun cleanupExpiredTokens(): Int {
        val expiredUserIds = queueTokenRepository.removeExpiredActiveTokens()
        return expiredUserIds.size
    }
}
