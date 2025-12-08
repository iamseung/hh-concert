package kr.hhplus.be.server.application.usecase.payment

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.model.PaymentModel
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.service.PointHistoryService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProcessPaymentUseCaseTest {

    private lateinit var processPaymentUseCase: ProcessPaymentUseCase
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

        processPaymentUseCase = ProcessPaymentUseCase(
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
    fun execute_Success() {
        // given
        val userId = 1L
        val reservationId = 1L
        val seatId = 1L
        val price = 100000
        val queueToken = "test-token"
        val command = ProcessPaymentCommand(
            userId = userId,
            reservationId = reservationId,
            queueToken = queueToken,
        )

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val reservation = ReservationModel.reconstitute(
            id = reservationId,
            userId = userId,
            seatId = seatId,
            reservationStatus = ReservationStatus.TEMPORARY,
            temporaryReservedAt = LocalDateTime.now(),
            temporaryExpiredAt = LocalDateTime.now().plusMinutes(5),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val seat = SeatModel.reconstitute(
            id = seatId,
            concertScheduleId = 1L,
            seatNumber = 1,
            price = price,
            seatStatus = SeatStatus.TEMPORARY_RESERVED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val payment = PaymentModel.reconstitute(
            id = 1L,
            reservationId = reservationId,
            userId = userId,
            amount = price,
            paymentStatus = PaymentStatus.COMPLETED,
            paymentAt = LocalDateTime.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val token = QueueTokenModel.reconstitute(
            userId = userId,
            token = queueToken,
            queueStatus = QueueStatus.ACTIVE,
            activatedAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusMinutes(10),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val point = PointModel.reconstitute(
            id = 1L,
            userId = userId,
            balance = 0,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { userService.findById(userId) } returns user
        every { reservationService.findByIdWithLock(reservationId) } returns reservation
        every { seatService.findById(seatId) } returns seat
        every { seatService.update(any()) } returns seat
        every { pointService.usePoint(userId, price) } returns point
        every { pointHistoryService.savePointHistory(userId, price, TransactionType.USE) } returns Unit
        every { paymentService.savePayment(any()) } returns payment
        every { reservationService.update(any()) } returns reservation
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns token
        every { queueTokenService.expireQueueToken(token) } returns token

        // when
        val result = processPaymentUseCase.execute(command)

        // then
        assertThat(result.paymentId).isEqualTo(payment.id)
        assertThat(result.reservationId).isEqualTo(reservationId)
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.amount).isEqualTo(price)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { reservationService.findByIdWithLock(reservationId) }
        verify(exactly = 1) { paymentService.savePayment(any()) }
    }
}
