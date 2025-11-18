package kr.hhplus.be.server.application.scheduler

import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class QueueScheduler(
    private val queueTokenService: QueueTokenService,
    private val queueTokenRepository: QueueTokenRepository,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val MAX_ACTIVE_USERS = 100
        private const val BATCH_ACTIVATION_SIZE = 10
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    fun activateWaitingTokens() {
        try {
            val activeCount = queueTokenRepository.countByStatus(QueueStatus.ACTIVE)
            val availableSlots = (MAX_ACTIVE_USERS - activeCount).toInt()

            if (availableSlots > 0) {
                val tokensToActivate = minOf(availableSlots, BATCH_ACTIVATION_SIZE)
                queueTokenService.activateWaitingTokens(tokensToActivate)

                log.info("Activated $tokensToActivate waiting tokens. Current active: ${activeCount + tokensToActivate}")
            }

            queueTokenService.updateWaitingPositions()
        } catch (e: Exception) {
            log.error("Error while activating waiting tokens", e)
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun cleanupExpiredTokens() {
        try {
            val expiredTokens = queueTokenRepository.findExpiredTokens()

            expiredTokens.forEach { token ->
                queueTokenService.expireQueueToken(token)
            }

            if (expiredTokens.isNotEmpty()) {
                log.info("Expired ${expiredTokens.size} tokens")
            }
        } catch (e: Exception) {
            log.error("Error while cleaning up expired tokens", e)
        }
    }
}