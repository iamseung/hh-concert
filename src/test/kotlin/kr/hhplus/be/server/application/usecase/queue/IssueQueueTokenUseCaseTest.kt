package kr.hhplus.be.server.application.usecase.queue

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class IssueQueueTokenUseCaseTest {

    private lateinit var issueQueueTokenUseCase: IssueQueueTokenUseCase
    private lateinit var userService: UserService
    private lateinit var queueTokenService: QueueTokenService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        queueTokenService = mockk()

        issueQueueTokenUseCase = IssueQueueTokenUseCase(
            userService = userService,
            queueTokenService = queueTokenService,
        )
    }

    @Test
    @DisplayName("대기열 토큰 발급 성공")
    fun execute_Success() {
        // given
        val userId = 1L
        val command = IssueQueueTokenCommand(userId = userId)

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
        )
        val queueToken = QueueTokenModel.reconstitute(
            userId = userId,
            token = "generated-token",
            queueStatus = QueueStatus.WAITING,
            activatedAt = null,
            expiresAt = null,
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
        )

        every { userService.findById(userId) } returns user
        every { queueTokenService.createQueueToken(userId) } returns queueToken
        every { queueTokenService.getQueuePosition(userId) } returns 5L

        // when
        val result = issueQueueTokenUseCase.execute(command)

        // then
        assertThat(result.token).isEqualTo(queueToken.token)
        assertThat(result.position).isEqualTo(5L)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { queueTokenService.createQueueToken(userId) }
    }
}
