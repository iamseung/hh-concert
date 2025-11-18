package kr.hhplus.be.server.queue.usecase

import io.mockk.*
import kr.hhplus.be.server.application.QueueFacade
import kr.hhplus.be.server.common.exception.AuthenticationException
import kr.hhplus.be.server.common.exception.AuthorizationException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class QueueUseCaseTest {

    private lateinit var queueFacade: QueueFacade
    private lateinit var userService: UserService
    private lateinit var queueTokenService: QueueTokenService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        queueTokenService = mockk()

        queueFacade = QueueFacade(
            userService = userService,
            queueTokenService = queueTokenService,
        )
    }

    @Test
    @DisplayName("대기열 토큰 발급 성공 - WAITING 상태로 생성")
    fun issueQueueToken_Success() {
        // given
        val userId = 1L
        val user = User.create("testUser", "test@test.com", "password")
        val queueToken = QueueToken.create(userId, 5)

        every { userService.getUser(userId) } returns user
        every { queueTokenService.createQueueToken(userId) } returns queueToken

        // when
        val result = queueFacade.issueQueueToken(userId)

        // then
        assertThat(result).isNotNull
        assertThat(result.queueStatus).isEqualTo(QueueStatus.WAITING)
        assertThat(result.queuePosition).isEqualTo(5)

        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { queueTokenService.createQueueToken(userId) }
    }

    @Test
    @DisplayName("대기 상태 조회 성공 - WAITING 상태")
    fun getQueueStatus_Success_Waiting() {
        // given
        val token = "test-token-uuid"
        val queueToken = QueueToken.create(1L, 10)

        every { queueTokenService.getQueueTokenByToken(token) } returns queueToken

        // when
        val result = queueFacade.getQueueStatus(token)

        // then
        assertThat(result).isNotNull
        assertThat(result.queueStatus).isEqualTo(QueueStatus.WAITING)
        assertThat(result.queuePosition).isEqualTo(10)
        assertThat(result.estimatedWaitTimeMinutes).isEqualTo(10)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
    }

    @Test
    @DisplayName("대기 상태 조회 성공 - ACTIVE 상태")
    fun getQueueStatus_Success_Active() {
        // given
        val token = "test-token-uuid"
        val queueToken = spyk(QueueToken.create(1L, 10))
        every { queueToken.queueStatus } returns QueueStatus.ACTIVE
        every { queueToken.queuePosition } returns 0

        every { queueTokenService.getQueueTokenByToken(token) } returns queueToken

        // when
        val result = queueFacade.getQueueStatus(token)

        // then
        assertThat(result).isNotNull
        assertThat(result.queueStatus).isEqualTo(QueueStatus.ACTIVE)
        assertThat(result.queuePosition).isEqualTo(0)
        assertThat(result.estimatedWaitTimeMinutes).isEqualTo(0)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
    }

    @Test
    @DisplayName("대기 상태 조회 실패 - 유효하지 않은 토큰")
    fun getQueueStatus_Fail_InvalidToken() {
        // given
        val token = "invalid-token"

        every { queueTokenService.getQueueTokenByToken(token) } throws AuthenticationException(ErrorCode.INVALID_TOKEN)

        // when & then
        assertThatThrownBy {
            queueFacade.getQueueStatus(token)
        }
            .isInstanceOf(AuthenticationException::class.java)
            .hasMessage(ErrorCode.INVALID_TOKEN.message)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
    }

    @Test
    @DisplayName("대기열 토큰 검증 성공 - ACTIVE 상태")
    fun validateQueueToken_Success() {
        // given
        val token = "test-token-uuid"
        val queueToken = spyk(QueueToken.create(1L, 5))

        every { queueTokenService.getQueueTokenByToken(token) } returns queueToken
        every { queueToken.validateActive() } just Runs

        // when & then
        queueFacade.validateQueueToken(token)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
        verify(exactly = 1) { queueToken.validateActive() }
    }

    @Test
    @DisplayName("대기열 토큰 검증 실패 - WAITING 상태 (아직 활성화 안됨)")
    fun validateQueueToken_Fail_NotActive() {
        // given
        val token = "test-token-uuid"
        val queueToken = spyk(QueueToken.create(1L, 5))

        every { queueTokenService.getQueueTokenByToken(token) } returns queueToken
        every { queueToken.validateActive() } throws AuthorizationException(ErrorCode.QUEUE_TOKEN_NOT_ACTIVE)

        // when & then
        assertThatThrownBy {
            queueFacade.validateQueueToken(token)
        }
            .isInstanceOf(AuthorizationException::class.java)
            .hasMessage(ErrorCode.QUEUE_TOKEN_NOT_ACTIVE.message)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
        verify(exactly = 1) { queueToken.validateActive() }
    }

    @Test
    @DisplayName("대기열 토큰 검증 실패 - 만료된 토큰")
    fun validateQueueToken_Fail_Expired() {
        // given
        val token = "test-token-uuid"
        val queueToken = spyk(QueueToken.create(1L, 5))

        every { queueTokenService.getQueueTokenByToken(token) } returns queueToken
        every { queueToken.validateActive() } throws AuthorizationException(ErrorCode.TOKEN_EXPIRED)

        // when & then
        assertThatThrownBy {
            queueFacade.validateQueueToken(token)
        }
            .isInstanceOf(AuthorizationException::class.java)
            .hasMessage(ErrorCode.TOKEN_EXPIRED.message)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
        verify(exactly = 1) { queueToken.validateActive() }
    }

    @Test
    @DisplayName("대기열 토큰 검증 실패 - 유효하지 않은 토큰")
    fun validateQueueToken_Fail_InvalidToken() {
        // given
        val token = "invalid-token"

        every { queueTokenService.getQueueTokenByToken(token) } throws AuthenticationException(ErrorCode.INVALID_TOKEN)

        // when & then
        assertThatThrownBy {
            queueFacade.validateQueueToken(token)
        }
            .isInstanceOf(AuthenticationException::class.java)
            .hasMessage(ErrorCode.INVALID_TOKEN.message)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(token) }
    }
}
