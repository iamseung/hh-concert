package kr.hhplus.be.server.application.usecase.concert

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GetAvailableSchedulesUseCaseTest {

    private lateinit var getAvailableSchedulesUseCase: GetAvailableSchedulesUseCase
    private lateinit var concertService: ConcertService
    private lateinit var concertScheduleService: ConcertScheduleService

    @BeforeEach
    fun setUp() {
        concertService = mockk()
        concertScheduleService = mockk()

        getAvailableSchedulesUseCase = GetAvailableSchedulesUseCase(
            concertService = concertService,
            concertScheduleService = concertScheduleService,
        )
    }

    @Test
    @DisplayName("예약 가능한 콘서트 일정 목록 조회 성공")
    fun execute_Success() {
        // given
        val concertId = 1L
        val command = GetAvailableSchedulesCommand(concertId = concertId)

        val concert = ConcertModel.reconstitute(
            id = concertId,
            title = "테스트 콘서트",
            description = "테스트 설명",
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
        )
        val schedule1 = ConcertScheduleModel.reconstitute(
            id = 1L,
            concertId = concertId,
            concertDate = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val schedule2 = ConcertScheduleModel.reconstitute(
            id = 2L,
            concertId = concertId,
            concertDate = LocalDateTime.now().plusDays(2),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { concertService.findById(concertId) } returns concert
        every { concertScheduleService.findByConcertId(concertId) } returns listOf(schedule1, schedule2)

        // when
        val result = getAvailableSchedulesUseCase.execute(command)

        // then
        assertThat(result.schedules).hasSize(2)
        assertThat(result.schedules[0].concertId).isEqualTo(concertId)
        assertThat(result.schedules[1].concertId).isEqualTo(concertId)
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }

    @Test
    @DisplayName("예약 가능한 일정이 없는 경우")
    fun execute_NoAvailableSchedules() {
        // given
        val concertId = 1L
        val command = GetAvailableSchedulesCommand(concertId = concertId)

        val concert = ConcertModel.reconstitute(
            id = concertId,
            title = "테스트 콘서트",
            description = "테스트 설명",
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
        )

        every { concertService.findById(concertId) } returns concert
        every { concertScheduleService.findByConcertId(concertId) } returns emptyList()

        // when
        val result = getAvailableSchedulesUseCase.execute(command)

        // then
        assertThat(result.schedules).isEmpty()
        verify(exactly = 1) { concertService.findById(concertId) }
        verify(exactly = 1) { concertScheduleService.findByConcertId(concertId) }
    }
}
