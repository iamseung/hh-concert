package kr.hhplus.be.server.reservation.usecase

import io.mockk.*
import kr.hhplus.be.server.application.ReservationFacade
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.model.QueueToken
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

class ReservationUseCaseTest {

    private lateinit var reservationFacade: ReservationFacade
    private lateinit var userService: UserService
    private lateinit var seatService: SeatService
    private lateinit var reservationService: ReservationService
    private lateinit var queueTokenService: QueueTokenService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        seatService = mockk()
        reservationService = mockk()
        queueTokenService = mockk()

        reservationFacade = ReservationFacade(
            userService = userService,
            seatService = seatService,
            reservationService = reservationService,
            queueTokenService = queueTokenService,
        )
    }

    @Test
    @DisplayName("예약 생성 성공 - 임시 예약 (TEMPORARY)")
    fun createReservation_Success() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val user = User.create("testUser", "test@test.com", "password")
        val seat = spyk(Seat.create(scheduleId, 1, 50000))
        val reservation = Reservation.create(userId, seatId)
        val queueToken = "test-queue-token"

        every { userService.getUser(userId) } returns user
        every { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) } returns seat
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockk(relaxed = true)
        every { seat.validateAvailable() } just Runs
        every { seat.temporaryReservation() } just Runs
        every { reservationService.save(any()) } returns reservation

        // when
        val result = reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)

        // then
        assertThat(result).isNotNull
        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) }
        verify(exactly = 1) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 생성 실패 - 좌석이 이미 예약됨")
    fun createReservation_Fail_SeatNotAvailable() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val user = User.create("testUser", "test@test.com", "password")
        val seat = spyk(Seat.create(scheduleId, 1, 50000))
        val queueToken = "test-queue-token"

        every { userService.getUser(userId) } returns user
        every { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) } returns seat
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockk(relaxed = true)
        every { seat.validateAvailable() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.SEAT_NOT_AVAILABLE)

        // when & then
        assertThatThrownBy {
            reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) }
        verify(exactly = 0) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 생성 실패 - 콘서트 일정이 만료됨")
    fun createReservation_Fail_ScheduleExpired() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val user = User.create("testUser", "test@test.com", "password")
        val seat = spyk(Seat.create(scheduleId, 1, 50000))
        val queueToken = "test-queue-token"
        val mockToken = mockk<QueueToken>()

        every { userService.getUser(userId) } returns user
        every { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) } returns seat
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockToken
        every { mockToken.validateActive() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.QUEUE_TOKEN_NOT_ACTIVE)

        // when & then
        assertThatThrownBy {
            reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) }
        verify(exactly = 0) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 조회 성공")
    fun getConcertReservations_Success() {
        // given
        val userId = 1L
        val user = User.create("testUser", "test@test.com", "password")
        val reservation = Reservation.create(userId, 1L)
        val reservations = listOf(reservation)

        every { userService.getUser(userId) } returns user
        every { reservationService.findAllByUserId(userId) } returns reservations

        // when
        val result = reservationFacade.getConcertReservations(userId)

        // then
        assertThat(result).isNotNull
        assertThat(result).hasSize(1)
        verify(exactly = 1) { userService.getUser(userId) }
        verify(exactly = 1) { reservationService.findAllByUserId(userId) }
    }
}
