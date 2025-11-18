package kr.hhplus.be.server.domain.queue.service

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import org.springframework.stereotype.Service

@Service
class QueueTokenService(
    private val queueTokenRepository: QueueTokenRepository,
) {

    fun createQueueToken(userId: Long): QueueToken {
        val existingActiveToken = queueTokenRepository.findAllByStatus(QueueStatus.ACTIVE)
            .find { it.userId == userId }
        if (existingActiveToken != null) {
            return existingActiveToken
        }

        val existingWaitingToken = queueTokenRepository.findAllByStatus(QueueStatus.WAITING)
            .find { it.userId == userId }
        if (existingWaitingToken != null) {
            return existingWaitingToken
        }

        val waitingCount = queueTokenRepository.countByStatus(QueueStatus.WAITING)
        val position = (waitingCount + 1).toInt()

        val queueToken = QueueToken.create(userId, position)
        return queueTokenRepository.save(queueToken)
    }

    fun getQueueTokenByToken(token: String): QueueToken {
        return queueTokenRepository.findByTokenOrThrow(token)
    }

    fun expireQueueToken(queueToken: QueueToken): QueueToken {
        queueToken.expire()
        return queueTokenRepository.save(queueToken)
    }

    fun updateWaitingPositions() {
        val waitingTokens = queueTokenRepository.findAllByStatus(QueueStatus.WAITING)

        waitingTokens.forEachIndexed { index, token ->
            token.updatePosition(index + 1)
            queueTokenRepository.save(token)
        }
    }

    fun activateWaitingTokens(count: Int) {
        val waitingTokens = queueTokenRepository.findAllByStatus(QueueStatus.WAITING)

        waitingTokens.take(count).forEach { token ->
            token.activate()
            queueTokenRepository.save(token)
        }
    }
}
