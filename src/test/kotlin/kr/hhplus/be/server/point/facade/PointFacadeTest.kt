package kr.hhplus.be.server.point.facade

import io.mockk.*
import kr.hhplus.be.server.application.PointFacade
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PointFacadeTest {

    private lateinit var pointFacade: PointFacade
    private lateinit var userService: UserService
    private lateinit var pointService: PointService
    private lateinit var pointHistoryService: PointHistoryService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        pointService = mockk()
        pointHistoryService = mockk()

        pointFacade = PointFacade(
            userService = userService,
            pointService = pointService,
            pointHistoryService = pointHistoryService,
        )
    }

    @Test
    @DisplayName("포인트 조회 성공")
    fun getPoints_Success() {
        // given
        val userId = 1L
        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val pointModel = PointModel.create(userId, 50000)

        every { userService.findById(userId) } returns userModel
        every { pointService.getPointByUserId(userId) } returns pointModel

        // when
        val result = pointFacade.getPoints(userId)

        // then
        assertThat(result).isNotNull
        assertThat(result.balance).isEqualTo(50000)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.getPointByUserId(userId) }
    }

    @Test
    @DisplayName("포인트 충전 성공")
    fun chargePoint_Success() {
        // given
        val userId = 1L
        val amount = 10000
        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val chargedPointModel = PointModel.create(userId, 60000)

        every { userService.findById(userId) } returns userModel
        every { pointService.chargePoint(userId, amount) } returns chargedPointModel
        every { pointHistoryService.savePointHistory(userModel, amount, TransactionType.CHARGE) } just Runs

        // when
        val result = pointFacade.chargePoint(userId, amount)

        // then
        assertThat(result).isNotNull
        assertThat(result.balance).isEqualTo(60000)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.chargePoint(userId, amount) }
        verify(exactly = 1) { pointHistoryService.savePointHistory(userModel, amount, TransactionType.CHARGE) }
    }

    @Test
    @DisplayName("포인트 충전 실패 - 잘못된 충전 금액 (0 이하)")
    fun chargePoint_Fail_InvalidAmount() {
        // given
        val userId = 1L
        val amount = -1000
        val userModel = UserModel.create("testUser", "test@test.com", "password")

        every { userService.findById(userId) } returns userModel
        every { pointService.chargePoint(userId, amount) } throws BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT)

        // when & then
        assertThatThrownBy {
            pointFacade.chargePoint(userId, amount)
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.chargePoint(userId, amount) }
        verify(exactly = 0) { pointHistoryService.savePointHistory(any(), any(), any()) }
    }

    @Test
    @DisplayName("포인트 충전 실패 - 0원 충전")
    fun chargePoint_Fail_ZeroAmount() {
        // given
        val userId = 1L
        val amount = 0
        val userModel = UserModel.create("testUser", "test@test.com", "password")

        every { userService.findById(userId) } returns userModel
        every { pointService.chargePoint(userId, amount) } throws BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT)

        // when & then
        assertThatThrownBy {
            pointFacade.chargePoint(userId, amount)
        }.isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CHARGE_AMOUNT)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { pointService.chargePoint(userId, amount) }
        verify(exactly = 0) { pointHistoryService.savePointHistory(any(), any(), any()) }
    }
}
