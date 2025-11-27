package kr.hhplus.be.server.domain.queue.service

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import org.springframework.stereotype.Service

@Service
class QueueTokenService(
    private val queueTokenRepository: QueueTokenRepository,
) {

    fun createQueueToken(userId: Long): QueueTokenModel {
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

        val queueTokenModel = QueueTokenModel.create(userId, position)
        return queueTokenRepository.save(queueTokenModel)
    }

    fun getQueueTokenByToken(token: String): QueueTokenModel {
        return queueTokenRepository.findByTokenOrThrow(token)
    }

    fun expireQueueToken(queueTokenModel: QueueTokenModel): QueueTokenModel {
        queueTokenModel.expire()
        return queueTokenRepository.save(queueTokenModel)
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
