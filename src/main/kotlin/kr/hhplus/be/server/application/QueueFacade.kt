package kr.hhplus.be.server.application

import kr.hhplus.be.server.api.dto.response.QueueStatusResponse
import kr.hhplus.be.server.api.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class QueueFacade(
    private val userService: UserService,
    private val queueTokenService: QueueTokenService,
) {

    fun issueQueueToken(userId: Long): QueueTokenResponse {
        val user = userService.findById(userId)
        val queueToken = queueTokenService.createQueueToken(user.id)

        return QueueTokenResponse.from(queueToken)
    }

    @Transactional(readOnly = true)
    fun getQueueStatus(token: String): QueueStatusResponse {
        val queueToken = queueTokenService.getQueueTokenByToken(token)
        return QueueStatusResponse.from(queueToken)
    }

    @Transactional(readOnly = true)
    fun validateQueueToken(token: String) {
        val queueToken = queueTokenService.getQueueTokenByToken(token)
        queueToken.validateActive()
    }
}
