package kr.hhplus.be.server.reservation.facade

import io.mockk.*
import kr.hhplus.be.server.application.ReservationFacade
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
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

class ReservationFacadeTest {

    private lateinit var reservationFacade: ReservationFacade
    private lateinit var userService: UserService
    private lateinit var seatService: SeatService
    private lateinit var reservationService: ReservationService
    private lateinit var queueTokenService: QueueTokenService
    private lateinit var concertScheduleService: kr.hhplus.be.server.domain.concert.service.ConcertScheduleService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        seatService = mockk()
        reservationService = mockk()
        queueTokenService = mockk()
        concertScheduleService = mockk()

        reservationFacade = ReservationFacade(
            userService = userService,
            seatService = seatService,
            reservationService = reservationService,
            queueTokenService = queueTokenService,
            concertScheduleService = concertScheduleService,
        )
    }

    @Test
    @DisplayName("예약 생성 성공 - 임시 예약 (TEMPORARY)")
    fun createReservation_Success() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val seatModel = spyk(SeatModel.create(scheduleId, 1, 50000))
        val reservationModel = ReservationModel.create(userId, seatId)
        val queueToken = "test-queue-token"
        val schedule = mockk<kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel>(relaxed = true)

        val mockToken = mockk<QueueTokenModel>(relaxed = true)
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockToken
        every { mockToken.validateActive() } just Runs
        every { userService.findById(userId) } returns userModel
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { schedule.id } returns scheduleId
        every { schedule.validateAvailable() } just Runs
        every { seatService.findByIdAndConcertScheduleIdWithLock(seatId, scheduleId) } returns seatModel
        every { seatModel.temporaryReservation() } just Runs
        every { reservationService.save(any()) } returns reservationModel

        // when
        val result = reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)

        // then
        assertThat(result).isNotNull
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
        verify(exactly = 1) { seatService.findByIdAndConcertScheduleIdWithLock(seatId, scheduleId) }
        verify(exactly = 1) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 생성 실패 - 좌석이 이미 예약됨")
    fun createReservation_Fail_SeatNotAvailable() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val seatModel = spyk(SeatModel.create(scheduleId, 1, 50000))
        val queueToken = "test-queue-token"
        val schedule = mockk<kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel>(relaxed = true)

        val mockToken = mockk<QueueTokenModel>(relaxed = true)
        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockToken
        every { mockToken.validateActive() } just Runs
        every { userService.findById(userId) } returns userModel
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { schedule.id } returns scheduleId
        every { schedule.validateAvailable() } just Runs
        every { seatService.findByIdAndConcertScheduleIdWithLock(seatId, scheduleId) } returns seatModel
        every { seatModel.temporaryReservation() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.SEAT_NOT_AVAILABLE)

        // when & then
        assertThatThrownBy {
            reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
        verify(exactly = 1) { seatService.findByIdAndConcertScheduleIdWithLock(seatId, scheduleId) }
        verify(exactly = 0) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 생성 실패 - 콘서트 일정이 만료됨")
    fun createReservation_Fail_ScheduleExpired() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L

        val queueToken = "test-queue-token"
        val mockToken = mockk<QueueTokenModel>()

        every { queueTokenService.getQueueTokenByToken(queueToken) } returns mockToken
        every { mockToken.validateActive() } throws BusinessException(kr.hhplus.be.server.common.exception.ErrorCode.QUEUE_TOKEN_NOT_ACTIVE)

        // when & then
        assertThatThrownBy {
            reservationFacade.createReservation(userId, scheduleId, seatId, queueToken)
        }.isInstanceOf(BusinessException::class.java)

        verify(exactly = 1) { queueTokenService.getQueueTokenByToken(queueToken) }
        verify(exactly = 0) { userService.findById(any()) }
        verify(exactly = 0) { concertScheduleService.findById(any()) }
        verify(exactly = 0) { reservationService.save(any()) }
    }

    @Test
    @DisplayName("예약 조회 성공")
    fun getConcertReservations_Success() {
        // given
        val userId = 1L
        val userModel = UserModel.create("testUser", "test@test.com", "password")
        val reservationModel = ReservationModel.create(userId, 1L)
        val reservations = listOf(reservationModel)

        every { userService.findById(userId) } returns userModel
        every { reservationService.findAllByUserId(userId) } returns reservations

        // when
        val result = reservationFacade.getConcertReservations(userId)

        // then
        assertThat(result).isNotNull
        assertThat(result).hasSize(1)
        verify(exactly = 1) { userService.findById(userId) }
        verify(exactly = 1) { reservationService.findAllByUserId(userId) }
    }
}
