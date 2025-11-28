package kr.hhplus.be.server.application.usecase.queue

import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssueQueueTokenUseCase(
    private val userService: UserService,
    private val queueTokenService: QueueTokenService,
) {

    @Transactional
    fun execute(command: IssueQueueTokenCommand): IssueQueueTokenResult {
        // 1. 사용자 검증
        val user = userService.findById(command.userId)

        // 2. 대기열 토큰 생성
        val queueToken = queueTokenService.createQueueToken(user.id)

        // 3. 결과 반환
        return IssueQueueTokenResult(
            token = queueToken.token,
            status = queueToken.queueStatus,
            position = queueToken.queuePosition.toLong(),
            createdAt = queueToken.createdAt
        )
    }
}
