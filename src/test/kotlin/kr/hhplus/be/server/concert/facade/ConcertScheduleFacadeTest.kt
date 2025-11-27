package kr.hhplus.be.server.concert.facade

import io.mockk.*
import kr.hhplus.be.server.application.ConcertScheduleFacade
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConcertScheduleFacadeTest {

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
        val concertModel = spyk(ConcertModel.create("Test Concert", "Test Description"))
        val schedule1 = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(10)))
        val schedule2 = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(20)))
        val schedules = listOf(schedule1, schedule2)

        every { concertModel.id } returns concertId
        every { schedule1.isAvailable } returns true
        every { schedule2.isAvailable } returns true
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).hasSize(2)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 조회 - 만료된 일정 필터링")
    fun getAvailableSchedules_FilterExpired() {
        // given
        val concertId = 1L
        val concertModel = spyk(ConcertModel.create("Test Concert", "Test Description"))
        val availableSchedule = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(10)))
        val expiredSchedule = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().minusDays(1)))
        val schedules = listOf(availableSchedule, expiredSchedule)

        every { concertModel.id } returns concertId
        every { availableSchedule.isAvailable } returns true
        every { expiredSchedule.isAvailable } returns false
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).hasSize(1)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 조회 - 모든 일정이 만료된 경우 빈 목록 반환")
    fun getAvailableSchedules_EmptyWhenAllExpired() {
        // given
        val concertId = 1L
        val concertModel = spyk(ConcertModel.create("Test Concert", "Test Description"))
        val expiredSchedule1 = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().minusDays(10)))
        val expiredSchedule2 = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().minusDays(5)))
        val schedules = listOf(expiredSchedule1, expiredSchedule2)

        every { concertModel.id } returns concertId
        every { expiredSchedule1.isAvailable } returns false
        every { expiredSchedule2.isAvailable } returns false
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findByConcertId(concertId) } returns schedules

        // when
        val result = concertScheduleFacade.getAvailableSchedules(concertId)

        // then
        assertThat(result).isEmpty()
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 성공")
    fun getAvailableSeats_Success() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concertModel = ConcertModel.create("Test Concert", "Test Description")
        val schedule = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(10)))
        val seatModel1 = spyk(SeatModel.create(scheduleId, 1, 50000))
        val seatModel2 = spyk(SeatModel.create(scheduleId, 2, 50000))
        val seats = listOf(seatModel1, seatModel2)

        every { seatModel1.isAvailable } returns true
        every { seatModel2.isAvailable } returns true
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { schedule.validateIsConcert(any()) } just Runs
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).hasSize(2)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 - 예약된 좌석 필터링")
    fun getAvailableSeats_FilterReserved() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concertModel = ConcertModel.create("Test Concert", "Test Description")
        val schedule = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(10)))
        val availableSeatModel = spyk(SeatModel.create(scheduleId, 1, 50000))
        val reservedSeatModel = spyk(SeatModel.create(scheduleId, 2, 50000))
        val temporarySeatModel = spyk(SeatModel.create(scheduleId, 3, 50000))
        val seats = listOf(availableSeatModel, reservedSeatModel, temporarySeatModel)

        every { availableSeatModel.isAvailable } returns true
        every { reservedSeatModel.isAvailable } returns false
        every { temporarySeatModel.isAvailable } returns false
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { schedule.validateIsConcert(any()) } just Runs
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).hasSize(1)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석 조회 - 모든 좌석이 예약된 경우 빈 목록 반환")
    fun getAvailableSeats_EmptyWhenAllReserved() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val concertModel = ConcertModel.create("Test Concert", "Test Description")
        val schedule = spyk(ConcertScheduleModel.create(concertId, LocalDate.now().plusDays(10)))
        val reservedSeatModel1 = spyk(SeatModel.create(scheduleId, 1, 50000))
        val reservedSeatModel2 = spyk(SeatModel.create(scheduleId, 2, 50000))
        val seats = listOf(reservedSeatModel1, reservedSeatModel2)

        every { reservedSeatModel1.isAvailable } returns false
        every { reservedSeatModel2.isAvailable } returns false
        every { concertService.findById(concertId) } returns concertModel
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { schedule.validateIsConcert(any()) } just Runs
        every { seatService.findAllByConcertScheduleId(scheduleId) } returns seats

        // when
        val result = concertScheduleFacade.getAvailableSeats(concertId, scheduleId)

        // then
        assertThat(result).isEmpty()
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
    }
}
