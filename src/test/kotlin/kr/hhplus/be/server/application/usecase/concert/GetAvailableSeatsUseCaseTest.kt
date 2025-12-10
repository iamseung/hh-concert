package kr.hhplus.be.server.application.usecase.concert

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GetAvailableSeatsUseCaseTest {

    private lateinit var getAvailableSeatsUseCase: GetAvailableSeatsUseCase
    private lateinit var concertService: ConcertService
    private lateinit var concertScheduleService: ConcertScheduleService
    private lateinit var seatService: SeatService

    @BeforeEach
    fun setUp() {
        concertService = mockk()
        concertScheduleService = mockk()
        seatService = mockk()

        getAvailableSeatsUseCase = GetAvailableSeatsUseCase(
            concertService = concertService,
            concertScheduleService = concertScheduleService,
            seatService = seatService,
        )
    }

    @Test
    @DisplayName("예약 가능한 좌석 목록 조회 성공")
    fun execute_Success() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val command = GetAvailableSeatsCommand(concertId = concertId, scheduleId = scheduleId)

        val concert = ConcertModel.reconstitute(
            id = concertId,
            title = "테스트 콘서트",
            description = "테스트 설명",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val schedule = ConcertScheduleModel.reconstitute(
            id = scheduleId,
            concertId = concertId,
            concertDate = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val seat1 = SeatModel.reconstitute(
            id = 1L,
            concertScheduleId = scheduleId,
            seatNumber = 1,
            price = 100000,
            seatStatus = SeatStatus.AVAILABLE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val seat2 = SeatModel.reconstitute(
            id = 2L,
            concertScheduleId = scheduleId,
            seatNumber = 2,
            price = 100000,
            seatStatus = SeatStatus.AVAILABLE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { concertService.findById(concertId) } returns concert
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { seatService.findAvailableSeatsByConcertScheduleId(scheduleId) } returns listOf(seat1, seat2)

        // when
        val result = getAvailableSeatsUseCase.execute(command)

        // then
        assertThat(result.seats).hasSize(2)
        assertThat(result.seats[0].seatNumber).isEqualTo(1)
        assertThat(result.seats[1].seatNumber).isEqualTo(2)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
        verify(exactly = 1) { seatService.findAvailableSeatsByConcertScheduleId(scheduleId) }
    }

    @Test
    @DisplayName("예약 가능한 좌석이 없는 경우")
    fun execute_NoAvailableSeats() {
        // given
        val concertId = 1L
        val scheduleId = 1L
        val command = GetAvailableSeatsCommand(concertId = concertId, scheduleId = scheduleId)

        val concert = ConcertModel.reconstitute(
            id = concertId,
            title = "테스트 콘서트",
            description = "테스트 설명",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val schedule = ConcertScheduleModel.reconstitute(
            id = scheduleId,
            concertId = concertId,
            concertDate = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { concertService.findById(concertId) } returns concert
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { seatService.findAvailableSeatsByConcertScheduleId(scheduleId) } returns emptyList()

        // when
        val result = getAvailableSeatsUseCase.execute(command)

        // then
        assertThat(result.seats).isEmpty()
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findById(scheduleId) }
        verify(exactly = 1) { seatService.findAvailableSeatsByConcertScheduleId(scheduleId) }
    }
}
