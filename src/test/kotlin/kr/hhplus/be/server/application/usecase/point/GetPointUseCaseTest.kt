package kr.hhplus.be.server.application.usecase.point

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime.*

class GetPointUseCaseTest {

    private lateinit var getPointUseCase: GetPointUseCase
    private lateinit var userService: UserService
    private lateinit var pointService: PointService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        pointService = mockk()

        getPointUseCase = GetPointUseCase(
            userService = userService,
            pointService = pointService,
        )
    }

    @Test
    @DisplayName("포인트 조회 성공")
    fun execute_Success() {
        // given
        val userId = 1L
        val command = GetPointCommand(userId = userId)

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = now(),
            updatedAt = now(),
        )
        val point = PointModel.reconstitute(
            id = 1L,
            userId = userId,
            balance = 50000,
            createdAt = now(),
            updatedAt = now(),
        )

        every { userService.findById(userId) } returns user
        every { pointService.getPointByUserId(userId) } returns point

        // when
        val result = getPointUseCase.execute(command)

        // then
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.balance).isEqualTo(50000)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.getPointByUserId(userId) }
    }
}
