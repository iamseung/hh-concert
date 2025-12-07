package kr.hhplus.be.server.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.dto.request.CreateReservationRequest
import kr.hhplus.be.server.api.dto.request.IssueQueueTokenRequest
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.queue.repository.RedisQueueRepository
import kr.hhplus.be.server.infrastructure.persistence.reservation.repository.ReservationJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import kr.hhplus.be.server.support.AbstractIntegrationContainerBaseTest
import org.springframework.data.redis.core.RedisTemplate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@AutoConfigureMockMvc
@DisplayName("예약 컨트롤러 API 통합 테스트")
class ReservationControllerTest : AbstractIntegrationContainerBaseTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var concertJpaRepository: ConcertJpaRepository

    @Autowired
    private lateinit var concertScheduleJpaRepository: ConcertScheduleJpaRepository

    @Autowired
    private lateinit var seatJpaRepository: SeatJpaRepository

    @Autowired
    private lateinit var reservationJpaRepository: ReservationJpaRepository

    @Autowired
    private lateinit var redisQueueRepository: RedisQueueRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @BeforeEach
    fun setUp() {
        cleanupDatabase()
    }

    @AfterEach
    fun tearDown() {
        cleanupDatabase()
    }

    private fun cleanupDatabase() {
        reservationJpaRepository.deleteAll()
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        // Redis 전체 flush (테스트 환경이므로 안전)
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    private fun issueAndActivateToken(userId: Long): String {
        // 1. 토큰 생성 및 WAITING 상태로 저장
        val queueTokenModel = QueueTokenModel.create(userId)
        redisQueueRepository.save(queueTokenModel) // 내부에서 addToWaitingQueue 호출됨

        // 2. ACTIVE 상태로 활성화
        redisQueueRepository.activateWaitingUsers(1)

        return queueTokenModel.token
    }

    // TODO
    @Test
    @DisplayName("POST /api/v1/concerts/{concertId}/reservations - 좌석 예약 성공")
    fun createReservationSuccess() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        val token = issueAndActivateToken(user.id)

        val request = CreateReservationRequest(
            userId = user.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )

        // when & then
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.seatId").value(seat.id))
            .andExpect(jsonPath("$.reservationStatus").value("TEMPORARY"))
            .andExpect(jsonPath("$.temporaryReservedAt").isNotEmpty)
            .andExpect(jsonPath("$.temporaryExpiresAt").isNotEmpty)
    }

    @Test
    @DisplayName("POST /api/v1/concerts/{concertId}/reservations - 이미 예약된 좌석 예약 시도 시 실패")
    fun createReservationWithAlreadyReservedSeat() {
        // given
        val user1 = userJpaRepository.save(User("사용자1", "user1@example.com", "password"))
        val user2 = userJpaRepository.save(User("사용자2", "user2@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        val token1 = issueAndActivateToken(user1.id)

        // 첫 번째 사용자가 좌석 예약
        val request1 = CreateReservationRequest(
            userId = user1.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)),
        )

        val token2 = issueAndActivateToken(user2.id)

        // 두 번째 사용자가 같은 좌석 예약 시도
        val request2 = CreateReservationRequest(
            userId = user2.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )

        // when & then
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)),
        )
            .andExpect(status().is4xxClientError)
    }

    @Test
    @DisplayName("POST /api/v1/concerts/{concertId}/reservations - WAITING 상태 토큰으로 예약 시도 시 실패")
    fun createReservationWithWaitingToken() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        // 토큰 발급 (WAITING 상태 유지)
        val tokenRequest = IssueQueueTokenRequest(userId = user.id)
        val tokenResponse = mockMvc.perform(
            post("/api/v1/queue/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tokenRequest)),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val token = objectMapper.readTree(tokenResponse.response.contentAsString).get("token").asText()

        val request = CreateReservationRequest(
            userId = user.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )

        // when & then
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().is4xxClientError)
    }

    @Test
    @DisplayName("POST /api/v1/concerts/{concertId}/reservations - 잘못된 토큰으로 예약 시도 시 실패")
    fun createReservationWithInvalidToken() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        val request = CreateReservationRequest(
            userId = user.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )

        // when & then
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", "invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().is4xxClientError)
    }

    // TODO
    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/reservations - 예약 조회 성공")
    fun getConcertReservationsSuccess() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        val token = issueAndActivateToken(user.id)

        // 좌석 예약
        val request = CreateReservationRequest(
            userId = user.id,
            scheduleId = schedule.id,
            seatId = seat.id,
        )
        mockMvc.perform(
            post("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("User-Id", user.id)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].seatId").value(seat.id))
            .andExpect(jsonPath("$[0].reservationStatus").value("TEMPORARY"))
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{concertId}/reservations - 예약 이력이 없는 경우 빈 리스트 반환")
    fun getConcertReservationsEmpty() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))

        // when & then
        mockMvc.perform(
            get("/api/v1/concerts/{concertId}/reservations", concert.id)
                .header("User-Id", user.id)
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
