package kr.hhplus.be.server.integration.distributed

import kr.hhplus.be.server.application.usecase.reservation.CreateReservationCommand
import kr.hhplus.be.server.application.usecase.reservation.CreateReservationUseCase
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * CreateReservationUseCase 분산락 통합 테스트
 *
 * 테스트 목적:
 * 1. 동일 좌석에 대한 동시 예약 요청을 분산락으로 직렬화
 * 2. TOCTOU (Time-Of-Check To Time-Of-Use) 방지 - 토큰 재검증
 * 3. 원자성 보장 - 좌석 상태, 예약 생성, 토큰 만료가 모두 함께 커밋/롤백
 * 4. 락 격리 - 다른 좌석 예약은 독립적으로 처리
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("분산락 통합 테스트 - 좌석 예약")
class DistributedLockReservationTest @Autowired constructor(
    private val createReservationUseCase: CreateReservationUseCase,
    private val userJpaRepository: UserJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val queueTokenService: QueueTokenService,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val redissonClient: RedissonClient,
    private val redisQueueRepository: RedisQueueRepository,
) {

    companion object {
        @Container
        @ServiceConnection
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var user: User
    private lateinit var concert: Concert
    private lateinit var schedule: ConcertSchedule
    private lateinit var seat: Seat
    private var queueToken: String = ""

    @BeforeEach
    fun setUp() {
        user = User(userName = "testUser", email = "test@test.com", password = "password")
            .apply { userJpaRepository.save(this) }

        concert = Concert(title = "Test Concert", description = "Test Description")
            .apply { concertJpaRepository.save(this) }

        schedule = ConcertSchedule(
            concertId = concert.id,
            concertDate = LocalDateTime.now().plusDays(7),
        ).apply { concertScheduleJpaRepository.save(this) }

        seat = Seat(
            concertScheduleId = schedule.id,
            seatNumber = 1,
            price = 100000,
            seatStatus = SeatStatus.AVAILABLE,
        ).apply { seatJpaRepository.save(this) }

        // 대기열 토큰 생성 (ACTIVE 상태)
        val token = queueTokenService.createQueueToken(user.id)
        queueTokenService.activateWaitingTokens(1)
        queueToken = token.token

        // 토큰이 제대로 활성화되었는지 검증
        val activatedToken = queueTokenService.getQueueTokenByToken(queueToken)
        require(activatedToken.queueStatus == QueueStatus.ACTIVE) {
            "토큰이 ACTIVE 상태가 아닙니다: ${activatedToken.queueStatus}"
        }
    }

    @AfterEach
    fun cleanup() {
        reservationJpaRepository.deleteAll()
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        // Redis 락 및 큐 정리
        redissonClient.keys.flushdb()
    }

    @Test
    @DisplayName("[중복 예약 방지] 동일 좌석 50회 동시 예약 시도 - 1회만 성공")
    fun `should prevent duplicate reservations with distributed lock`() {
        // Given
        val attemptCount = 50

        val executor = Executors.newFixedThreadPool(attemptCount)
        val latch = CountDownLatch(attemptCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 50개 스레드가 동시에 좌석 예약 시도
        repeat(attemptCount) {
            executor.submit {
                runCatching {
                    createReservationUseCase.execute(
                        CreateReservationCommand(
                            userId = user.id,
                            scheduleId = schedule.id,
                            seatId = seat.id,
                            queueToken = queueToken,
                        ),
                    )
                }.fold(
                    onSuccess = { successCount.incrementAndGet() },
                    onFailure = { failCount.incrementAndGet() },
                ).also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 1회만 성공, 나머지는 실패
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(attemptCount - 1)

        // 좌석 상태 확인 (TEMPORARY_RESERVED)
        val resultSeat = seatJpaRepository.findById(seat.id).get()
        assertThat(resultSeat.seatStatus).isEqualTo(SeatStatus.TEMPORARY_RESERVED)

        // 예약 1건만 생성 확인
        val reservations = reservationJpaRepository.findAll()
        assertThat(reservations).hasSize(1)

        println("✅ 중복 예약 방지 성공: $attemptCount 회 시도, 1회 성공, ${failCount.get()}회 실패")
    }

    @Test
    @DisplayName("[락 격리] 서로 다른 좌석 동시 예약 - 독립적으로 병렬 처리")
    fun `should process reservations independently for different seats`() {
        // Given: 3개의 독립적인 좌석과 각각의 토큰 생성
        val testData = (1..3).map { idx ->
            val u = User(userName = "user$idx", email = "user$idx@test.com", password = "password")
                .apply { userJpaRepository.save(this) }
            val s = Seat(
                concertScheduleId = schedule.id,
                seatNumber = idx + 1,
                price = 100000,
                seatStatus = SeatStatus.AVAILABLE,
            ).apply { seatJpaRepository.save(this) }
            val token = queueTokenService.createQueueToken(u.id)
            queueTokenService.activateWaitingTokens(1)

            Triple(u, s, token.token)
        }

        val executor = Executors.newFixedThreadPool(testData.size)
        val latch = CountDownLatch(testData.size)
        val successCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        // When: 3개 좌석에 대해 동시 예약 (각자 다른 사용자와 토큰)
        testData.forEach { (u, s, token) ->
            executor.submit {
                runCatching {
                    createReservationUseCase.execute(
                        CreateReservationCommand(
                            userId = u.id,
                            scheduleId = schedule.id,
                            seatId = s.id,
                            queueToken = token,
                        ),
                    )
                }.onSuccess {
                    successCount.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val duration = System.currentTimeMillis() - startTime

        // Then: 3개 모두 성공 (서로 다른 좌석 = 독립적인 락)
        assertThat(successCount.get()).isEqualTo(3)

        println("✅ 락 격리 성공: ${testData.size}개 좌석 동시 처리, 총 소요시간 = ${duration}ms")
    }

    @Test
    @DisplayName("[원자성] 좌석 상태, 예약 생성, 토큰 만료가 함께 커밋")
    fun `should ensure atomicity of all reservation operations`() {
        // Given
        val initialReservationCount = reservationJpaRepository.count()

        // When: 정상 예약
        val result = createReservationUseCase.execute(
            CreateReservationCommand(
                userId = user.id,
                scheduleId = schedule.id,
                seatId = seat.id,
                queueToken = queueToken,
            ),
        )

        // Then: 3개 작업이 모두 성공
        // 1. 좌석 상태 변경
        val resultSeat = seatJpaRepository.findById(seat.id).get()
        assertThat(resultSeat.seatStatus).isEqualTo(SeatStatus.TEMPORARY_RESERVED)

        // 2. 예약 생성
        val reservationCount = reservationJpaRepository.count()
        assertThat(reservationCount).isEqualTo(initialReservationCount + 1)
        assertThat(result.status).isEqualTo(ReservationStatus.TEMPORARY)

        // 3. 토큰 만료
        val resultToken = queueTokenService.getQueueTokenByToken(queueToken)
        assertThat(resultToken.queueStatus).isEqualTo(QueueStatus.EXPIRED)

        println("""
            ✅ 원자성 보장: 3개 작업 함께 커밋
            - 좌석 상태: AVAILABLE → TEMPORARY_RESERVED
            - 예약 생성: ${result.reservationId}
            - 토큰 상태: ACTIVE → EXPIRED
        """.trimIndent())
    }

    @Test
    @DisplayName("[성능] 독립적인 좌석 예약의 병렬 처리 성능")
    fun `should measure performance of parallel seat reservations`() {
        // Given: 10개의 독립적인 좌석과 사용자
        val testData = (1..10).map { idx ->
            val u = User(userName = "user$idx", email = "user$idx@test.com", password = "password")
                .apply { userJpaRepository.save(this) }
            val s = Seat(
                concertScheduleId = schedule.id,
                seatNumber = 10 + idx,
                price = 100000,
                seatStatus = SeatStatus.AVAILABLE,
            ).apply { seatJpaRepository.save(this) }
            val token = queueTokenService.createQueueToken(u.id)
            queueTokenService.activateWaitingTokens(1)
            val activeToken = token.token

            Triple(u, s, activeToken)
        }

        val executor = Executors.newFixedThreadPool(testData.size)
        val latch = CountDownLatch(testData.size)
        val successCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        // When: 10개 예약 동시 처리
        testData.forEach { (u, s, token) ->
            executor.submit {
                runCatching {
                    createReservationUseCase.execute(
                        CreateReservationCommand(
                            userId = u.id,
                            scheduleId = schedule.id,
                            seatId = s.id,
                            queueToken = token,
                        ),
                    )
                }.onSuccess {
                    successCount.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val totalDuration = System.currentTimeMillis() - startTime
        val avgDuration = totalDuration / testData.size

        // Then: 10개 모두 성공
        assertThat(successCount.get()).isEqualTo(testData.size)
        assertThat(avgDuration).isLessThan(500) // 평균 500ms 이하

        println("""
            ✅ 성능 측정:
            - 총 요청: ${testData.size} 회
            - 총 소요시간: ${totalDuration}ms
            - 평균 처리시간: ${avgDuration}ms
            - 성공: ${successCount.get()}
        """.trimIndent())
    }
}
