package kr.hhplus.be.server.concert.usecase

import io.mockk.*
import kr.hhplus.be.server.application.ConcertScheduleFacade
import kr.hhplus.be.server.domain.concert.model.Concert
import kr.hhplus.be.server.domain.concert.model.ConcertSchedule
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConcertScheduleUseCaseTest {

    private lateinit var concertScheduleFacade: ConcertScheduleFacade
    private lateinit var concertService: ConcertService
    private lateinit var concertScheduleService: ConcertScheduleService
    private lateinit var seatService: SeatService

    @BeforeEach
    fun setUp() {
        concertService = mockk()
        concertScheduleService = mockk()
        seatService = mockk()

        concertScheduleFacade = ConcertScheduleFacade(
            concertService = concertService,
            concertScheduleService = concertScheduleService,
            seatService = seatService,
        )
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 조회 성공")
    fun getAvailableSchedules_Success() {
        // given
        val concertId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val schedule1 = spyk(ConcertSchedule.create(concertId, LocalDate.now().plusDays(10)))
        val schedule2 = spyk(ConcertSchedule.create(concertId, LocalDate.now().plusDays(20)))
        val schedules = listOf(schedule1, schedule2)

        every { schedule1.isAvailable } returns true
        every { schedule2.isAvailable } returns true
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).hasSize(2)
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 조회 - 만료된 일정 필터링")
    fun getAvailableSchedules_FilterExpired() {
        // given
        val concertId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val availableSchedule = spyk(ConcertSchedule.create(concertId, LocalDate.now().plusDays(10)))
        val expiredSchedule = spyk(ConcertSchedule.create(concertId, LocalDate.now().minusDays(1)))
        val schedules = listOf(availableSchedule, expiredSchedule)

        every { availableSchedule.isAvailable } returns true
        every { expiredSchedule.isAvailable } returns false
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).hasSize(1)
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 조회 - 모든 일정이 만료된 경우 빈 목록 반환")
    fun getAvailableSchedules_EmptyWhenAllExpired() {
        // given
        val concertId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val expiredSchedule1 = spyk(ConcertSchedule.create(concertId, LocalDate.now().minusDays(10)))
        val expiredSchedule2 = spyk(ConcertSchedule.create(concertId, LocalDate.now().minusDays(5)))
        val schedules = listOf(expiredSchedule1, expiredSchedule2)

        every { expiredSchedule1.isAvailable } returns false
        every { expiredSchedule2.isAvailable } returns false
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).isEmpty()
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 성공")
    fun getAvailableSeats_Success() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val schedule = ConcertSchedule.create(concertId, LocalDate.now().plusDays(10))
        val seat1 = spyk(Seat.create(scheduleId, 1, 50000))
        val seat2 = spyk(Seat.create(scheduleId, 2, 50000))
        val seats = listOf(seat1, seat2)

        every { seat1.isAvailable } returns true
        every { seat2.isAvailable } returns true
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) } returns schedule
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).hasSize(2)
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 - 예약된 좌석 필터링")
    fun getAvailableSeats_FilterReserved() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val schedule = ConcertSchedule.create(concertId, LocalDate.now().plusDays(10))
        val availableSeat = spyk(Seat.create(scheduleId, 1, 50000))
        val reservedSeat = spyk(Seat.create(scheduleId, 2, 50000))
        val temporarySeat = spyk(Seat.create(scheduleId, 3, 50000))
        val seats = listOf(availableSeat, reservedSeat, temporarySeat)

        every { availableSeat.isAvailable } returns true
        every { reservedSeat.isAvailable } returns false
        every { temporarySeat.isAvailable } returns false
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) } returns schedule
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).hasSize(1)
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 - 모든 좌석이 예약된 경우 빈 목록 반환")
    fun getAvailableSeats_EmptyWhenAllReserved() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concert = Concert.create("Test Concert", "Test Description")
        val schedule = ConcertSchedule.create(concertId, LocalDate.now().plusDays(10))
        val reservedSeat1 = spyk(Seat.create(scheduleId, 1, 50000))
        val reservedSeat2 = spyk(Seat.create(scheduleId, 2, 50000))
        val seats = listOf(reservedSeat1, reservedSeat2)

        every { reservedSeat1.isAvailable } returns false
        every { reservedSeat2.isAvailable } returns false
        every { concertService.getConcert(concertId) } returns concert
        every { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) } returns schedule
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).isEmpty()
        verify(exactly = 1) { concertService.getConcert(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertIdAndId(concertId, scheduleId) }
    }
}
