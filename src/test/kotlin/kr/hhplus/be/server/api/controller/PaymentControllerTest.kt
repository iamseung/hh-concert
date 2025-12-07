package kr.hhplus.be.server.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.dto.request.ChargePointRequest
import kr.hhplus.be.server.api.dto.request.CreateReservationRequest
import kr.hhplus.be.server.api.dto.request.IssueQueueTokenRequest
import kr.hhplus.be.server.api.dto.request.ProcessPaymentRequest
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.payment.repository.PaymentJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.queue.repository.RedisQueueRepository
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@AutoConfigureMockMvc
@DisplayName("결제 컨트롤러 API 통합 테스트")
class PaymentControllerTest : AbstractIntegrationContainerBaseTest() {

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
    private lateinit var paymentJpaRepository: PaymentJpaRepository

    @Autowired
    private lateinit var pointJpaRepository: PointJpaRepository

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
        paymentJpaRepository.deleteAll()
        reservationJpaRepository.deleteAll()
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        // Redis 전체 flush (테스트 환경이므로 안전)
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    private fun issueAndActivateToken(userId: Long): String {
        // 토큰 생성 및 WAITING 상태로 저장
        val queueTokenModel = QueueTokenModel.create(userId)
        redisQueueRepository.save(queueTokenModel)

        // ACTIVE 상태로 활성화
        redisQueueRepository.activateWaitingUsers(1)

        return queueTokenModel.token
    }

    private fun chargePoints(userId: Long, amount: Int) {
        // 직접 DB에 포인트 저장
        pointJpaRepository.save(Point(userId, amount))
    }

    private fun createReservation(userId: Long, concertId: Long, scheduleId: Long, seatId: Long, token: String): Long {
        // 직접 DB에 예약 저장 (API 호출 대신)
        val seat = seatJpaRepository.findById(seatId).orElseThrow()
        seat.seatStatus = SeatStatus.TEMPORARY_RESERVED
        seatJpaRepository.save(seat)

        val reservation = Reservation(userId = userId, seatId = seatId)
        reservation.reservationStatus = ReservationStatus.TEMPORARY
        val saved = reservationJpaRepository.save(reservation)

        return saved.id
    }

    // TODO
    @Test
    @DisplayName("POST /api/payments - 결제 처리 성공")
    fun processPaymentSuccess() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        // 포인트 충전
        chargePoints(user.id, 100000)

        // 토큰 발급 및 활성화
        val token = issueAndActivateToken(user.id)

        // 좌석 예약
        val reservationId = createReservation(user.id, concert.id, schedule.id, seat.id, token)

        val request = ProcessPaymentRequest(reservationId = reservationId)

        // when & then
        mockMvc.perform(
            post("/api/payments")
                .header("User-Id", user.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paymentId").isNumber)
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.userId").value(user.id))
            .andExpect(jsonPath("$.amount").value(50000))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
    }

    @Test
    @DisplayName("POST /api/payments - 잘못된 예약 ID로 결제 시도 시 실패")
    fun processPaymentWithInvalidReservationId() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))

        // 토큰 발급 및 활성화
        val token = issueAndActivateToken(user.id)

        val request = ProcessPaymentRequest(reservationId = 999L)

        // when & then
        mockMvc.perform(
            post("/api/payments")
                .header("User-Id", user.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().is4xxClientError)
    }

    @Test
    @DisplayName("POST /api/payments - 포인트 부족 시 결제 실패")
    fun processPaymentWithInsufficientPoints() {
        // given
        val user = userJpaRepository.save(User("테스트", "test@example.com", "password"))
        val concert = concertJpaRepository.save(Concert("테스트 콘서트", null))
        val schedule = concertScheduleJpaRepository.save(
            ConcertSchedule(concert.id, LocalDateTime.now().plusDays(7)),
        )
        val seat = seatJpaRepository.save(Seat(schedule.id, 1, SeatStatus.AVAILABLE, 50000))

        // 포인트 충전 (부족한 금액)
        chargePoints(user.id, 10000)

        // 토큰 발급 및 활성화
        val token = issueAndActivateToken(user.id)

        // 좌석 예약
        val reservationId = createReservation(user.id, concert.id, schedule.id, seat.id, token)

        val request = ProcessPaymentRequest(reservationId = reservationId)

        // when & then
        mockMvc.perform(
            post("/api/payments")
                .header("User-Id", user.id)
                .header("X-Queue-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().is4xxClientError)
    }
}
