package kr.hhplus.be.server.payment.usecase

import io.mockk.*
import kr.hhplus.be.server.application.PaymentFacade
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.payment.model.Payment
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PaymentUseCaseTest {

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

        val user = User.create("testUser", "test@test.com", "password")
        val reservation = spyk(Reservation.create(userId, seatId))
        val seat = spyk(Seat.create(1L, 1, seatPrice))
        val payment = Payment.create(reservationId, userId, seatPrice)
        val queueToken = "test-queue-token"

        every { userService.getUser(userId) } returns user
        every { reservationService.findById(reservationId) } returns reservation
        every { reservation.validateOwnership(userId) } just Runs
        every { reservation.validatePayable() } just Runs
        every { reservation.seatId } returns seatId
        every { seatService.findById(seatId) } returns seat
        every { seat.confirmReservation() } just Runs
        every { reservation.confirmPayment() } just Runs
        every { pointService.usePoint(userId, seatPrice) } returns mockk()
        every { pointHistoryService.savePointHistory(userId, seatPrice, TransactionType.USE) } just Runs
        every { paymentService.savePayment(any()) } returns payment
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockk(relaxed = true)
        every { queueTokenService.expireQueueToken(any()) } returns mockk(relaxed = true)

        // when
        val result = paymentFacade.processPayment(userId, reservationId, queueToken)

        // then
        assertThat(result).isNotNull
        assertThat(result.amount).isEqualTo(seatPrice)
        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { reservationService.findById(reservationId) }
        verify(exactly = 1) { pointService.usePoint(userId, seatPrice) }
        verify(exactly = 1) { pointHistoryService.savePointHistory(userId, seatPrice, TransactionType.USE) }
        verify(exactly = 1) { paymentService.savePayment(any()) }
    }

    @Test
    @DisplayName("결제 처리 실패 - 유효하지 않은 사용자")
    fun processPayment_Fail_InvalidUser() {
        // given
        val userId = 1L
        val reservationId = 1L
        val queueToken = "test-queue-token"

        every { userService.getUser(userId) } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.USER_NOT_FOUND)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 0) { reservationService.findById(any()) }
    }

    @Test
    @DisplayName("결제 처리 실패 - 이미 확정된 예약")
    fun processPayment_Fail_AlreadyConfirmed() {
        // given
        val userId = 1L
        val reservationId = 1L
        val queueToken = "test-queue-token"

        val user = User.create("testUser", "test@test.com", "password")
        val reservation = spyk(Reservation.create(userId, 1L))

        every { userService.getUser(userId) } returns user
        every { reservationService.findById(reservationId) } returns reservation
        every { reservation.validateOwnership(userId) } just Runs
        every { reservation.validatePayable() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.RESERVATION_ALREADY_CONFIRMED)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.getUser(userId) }
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

        val user = User.create("testUser", "test@test.com", "password")
        val reservation = spyk(Reservation.create(userId, seatId))
        val seat = spyk(Seat.create(1L, 1, seatPrice))

        every { userService.getUser(userId) } returns user
        every { reservationService.findById(reservationId) } returns reservation
        every { reservation.validateOwnership(userId) } just Runs
        every { reservation.validatePayable() } just Runs
        every { reservation.seatId } returns seatId
        every { seatService.findById(seatId) } returns seat
        every { pointService.usePoint(userId, seatPrice) } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.INSUFFICIENT_POINTS)

        // when & then
        assertThatThrownBy {
            paymentFacade.processPayment(userId, reservationId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { reservationService.findById(reservationId) }
        verify(exactly = 1) { pointService.usePoint(userId, seatPrice) }
        verify(exactly = 0) { paymentService.savePayment(any()) }
    }
}
