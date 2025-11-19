package kr.hhplus.be.server.payment.facade

import io.mockk.*
import kr.hhplus.be.server.application.PaymentFacade
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PaymentFacadeTest {

    private lateinit var paymentFacade: PaymentFacade
    private lateinit var userService: UserService
    private lateinit var reservationService: ReservationService
    private lateinit var seatService: SeatService
    private lateinit var pointService: PointService
    private lateinit var pointHistoryService: PointHistoryService
    private lateinit var paymentService: PaymentService
    private lateinit var queueTokenService: QueueTokenService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        reservationService = mockk()
        seatService = mockk()
        pointService = mockk()
        pointHistoryService = mockk()
        paymentService = mockk()
        queueTokenService = mockk()

        paymentFacade = PaymentFacade(
            userService = userService,
            reservationService = reservationService,
            seatService = seatService,
            pointService = pointService,
            pointHistoryService = pointHistoryService,
            paymentService = paymentService,
            queueTokenService = queueTokenService,
        )
    }

    @Test
    @DisplayName("결제 처리 성공")
    fun processPayment_Success() {
        // given
        val userId = 1L
        val reservationId = 1L
        val seatId = 1L
        val seatPrice = 50000

        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val reservationModel = spyk(ReservationModel.create(userId, seatId))
        val seatModel = spyk(SeatModel.create(1L, 1, seatPrice))
        val paymentModel = PaymentModel.create(reservationId, userId, seatPrice)
        val queueToken = "test-queue-token"

        every { userService.findById(userId) } returns userModel
        every { reservationService.findById(reservationId) } returns reservationModel
        every { reservationModel.validateOwnership(userId) } just Runs
        every { reservationModel.validatePayable() } just Runs
        every { reservationModel.seatId } returns seatId
        every { seatService.findById(seatId) } returns seatModel
        every { seatModel.confirmReservation() } just Runs
        every { reservationModel.confirmPayment() } just Runs
        every { reservationService.save(reservationModel) } returns reservationModel
        every { pointService.usePoint(userId, seatPrice) } returns mockk()
        every { pointHistoryService.savePointHistory(userModel, seatPrice, TransactionType.USE) } just Runs
        every { paymentService.savePayment(any()) } returns paymentModel
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockk(relaxed = true)
        every { queueTokenService.expireQueueToken(any()) } returns mockk(relaxed = true)

        // when
        val result = paymentFacade.processPayment(userId, reservationId, queueToken)

        // then
        assertThat(result).isNotNull
        assertThat(result.amount).isEqualTo(seatPrice)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { reservationService.findById(reservationId) }
        verify(exactly = 1) { pointService.usePoint(userId, seatPrice) }
        verify(exactly = 1) { pointHistoryService.savePointHistory(userModel, seatPrice, TransactionType.USE) }
        verify(exactly = 1) { paymentService.savePayment(any()) }
    }

    @Test
    @DisplayName("결제 처리 실패 - 유효하지 않은 사용자")
    fun processPayment_Fail_InvalidUser() {
        // given
        val userId = 1L
        val reservationId = 1L
        val queueToken = "test-queue-token"

        every { userService.findById(userId) } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.USER_NOT_FOUND)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 0) { reservationService.findById(any()) }
    }

    @Test
    @DisplayName("결제 처리 실패 - 이미 확정된 예약")
    fun processPayment_Fail_AlreadyConfirmed() {
        // given
        val userId = 1L
        val reservationId = 1L
        val queueToken = "test-queue-token"

        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val reservationModel = spyk(ReservationModel.create(userId, 1L))

        every { userService.findById(userId) } returns userModel
        every { reservationService.findById(reservationId) } returns reservationModel
        every { reservationModel.validateOwnership(userId) } just Runs
        every { reservationModel.validatePayable() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.RESERVATION_ALREADY_CONFIRMED)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { reservationService.findById(reservationId) }
        verify(exactly = 0) { pointService.usePoint(any(), any()) }
    }

    @Test
    @DisplayName("결제 처리 실패 - 포인트 부족")
    fun processPayment_Fail_InsufficientPoints() {
        // given
        val userId = 1L
        val reservationId = 1L
        val seatId = 1L
        val seatPrice = 50000
        val queueToken = "test-queue-token"

        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val reservationModel = spyk(ReservationModel.create(userId, seatId))
        val seatModel = spyk(SeatModel.create(1L, 1, seatPrice))

        every { userService.findById(userId) } returns userModel
        every { reservationService.findById(reservationId) } returns reservationModel
        every { reservationModel.validateOwnership(userId) } just Runs
        every { reservationModel.validatePayable() } just Runs
        every { reservationModel.seatId } returns seatId
        every { seatService.findById(seatId) } returns seatModel
        every { pointService.usePoint(userId, seatPrice) } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.INSUFFICIENT_POINTS)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { reservationService.findById(reservationId) }
        verify(exactly = 1) { pointService.usePoint(userId, seatPrice) }
        verify(exactly = 0) { paymentService.savePayment(any()) }
    }
}
