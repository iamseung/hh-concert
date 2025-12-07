package kr.hhplus.be.server.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.support.AbstractIntegrationContainerBaseTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@AutoConfigureMockMvc
@DisplayName("콘서트 컨트롤러 API 통합 테스트")
class ConcertControllerTest : AbstractIntegrationContainerBaseTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var concertJpaRepository: ConcertJpaRepository

    @Autowired
    private lateinit var concertScheduleJpaRepository: ConcertScheduleJpaRepository

    @Autowired
    private lateinit var seatJpaRepository: SeatJpaRepository

    @BeforeEach
    fun setUp() {
        cleanupDatabase()
    }

    @AfterEach
    fun tearDown() {
        cleanupDatabase()
    }

    private fun cleanupDatabase() {
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/schedules - 예약 가능한 날짜 목록 조회 성공")
    fun getAvailableSchedulesSuccess() {
        // given
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule1 = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val schedule2 = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(14)),
        )

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/schedules", concert.id)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(schedule1.id))
            .andExpect(jsonPath("$[0].concertId").value(concert.id))
            .andExpect(jsonPath("$[1].id").value(schedule2.id))
            .andExpect(jsonPath("$[1].concertId").value(concert.id))
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/schedules - 존재하지 않는 콘서트 조회 시 404")
    fun getAvailableSchedulesNotFound() {
        // given
        val nonExistentConcertId = 999L

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/schedules", nonExistentConcertId)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats - 예약 가능한 좌석 조회 성공")
    fun getAvailableSeatsSuccess() {
        // given
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat1 = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))
        val seat2 = seatJpaRepository.save(Seat(schedule.id, 2, SeatStatus.AVAILABLE, 50000))

        // when & then
        mockMvc.perform(
            get(
                "/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats",
                concert.id,
                schedule.id,
            )
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(seat1.id))
            .andExpect(jsonPath("$[0].scheduleId").value(schedule.id))
            .andExpect(jsonPath("$[0].seatNumber").value(1))
            .andExpect(jsonPath("$[0].price").value(50000))
            .andExpect(jsonPath("$[0].seatStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$[1].id").value(seat2.id))
            .andExpect(jsonPath("$[1].seatNumber").value(2))
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/schedules/{scheduleId}/seats - 존재하지 않는 스케줄 조회 시 404")
    fun getAvailableSeatsNotFound() {
        // given
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val nonExistentScheduleId = 999L

        // when & then
        mockMvc.perform(
            get(
                "/api/v1/concerts/{concertId}/schedules/{scheduleId}/seats",
                concert.id,
                nonExistentScheduleId,
            )
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isNotFound)
    }
}
