package kr.hhplus.be.server.application.usecase.point

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class ChargePointUseCaseTest {

    private lateinit var chargePointUseCase: ChargePointUseCase
    private lateinit var userService: UserService
    private lateinit var pointService: PointService
    private lateinit var pointHistoryService: PointHistoryService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        pointService = mockk()
        pointHistoryService = mockk()

        chargePointUseCase = ChargePointUseCase(
            userService = userService,
            pointService = pointService,
            pointHistoryService = pointHistoryService,
        )
    }

    @Test
    @DisplayName("포인트 충전 성공")
    fun execute_Success() {
        // given
        val userId = 1L
        val amount = 10000
        val command = ChargePointCommand(userId = userId, amount = amount)

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val chargedPoint = PointModel.reconstitute(
            id = 1L,
            userId = userId,
            balance = 10000,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { userService.findById(userId) } returns user
        every { pointService.chargePoint(userId, amount) } returns chargedPoint
        every { pointHistoryService.savePointHistory(userId, amount, TransactionType.CHARGE) } returns Unit

        // when
        val result = chargePointUseCase.execute(command)

        // then
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.balance).isEqualTo(10000)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.chargePoint(userId, amount) }
        verify(exactly = 1) { pointHistoryService.savePointHistory(userId, amount, TransactionType.CHARGE) }
    }

    @Test
    @DisplayName("음수의 포인트는 충전 실패")
    fun execute_Fail() {
        // given
        val userId = 1L
        val amount = -10000
        val command = ChargePointCommand(userId = userId, amount = amount)

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { userService.findById(userId) } returns user
        every { pointService.chargePoint(userId, amount) } throws BusinessException(
            ErrorCode.INVALID_CHARGE_AMOUNT
        )

        // when & then
        assertThrows<BusinessException> {
            chargePointUseCase.execute(command)
        }

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.chargePoint(userId, amount) }
        verify(exactly = 0) { pointHistoryService.savePointHistory(any(), any(), any()) }
    }
}
