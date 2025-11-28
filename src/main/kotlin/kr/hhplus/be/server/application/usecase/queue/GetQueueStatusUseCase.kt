package kr.hhplus.be.server.application.usecase.queue

import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetQueueStatusUseCase(
    private val queueTokenService: QueueTokenService,
) {

    @Transactional(readOnly = true)
    fun execute(command: GetQueueStatusCommand): GetQueueStatusResult {
        // 1. 대기열 토큰 조회
        val queueToken = queueTokenService.getQueueTokenByToken(command.token)

        // 2. 결과 반환
        return GetQueueStatusResult(
            token = queueToken.token,
            status = queueToken.queueStatus,
            position = queueToken.queuePosition.toLong()
        )
    }
}
