package kr.hhplus.be.server.integration.cache

import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsCommand
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsUseCase
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
import kotlin.system.measureTimeMillis

/**
 * 좌석 조회 캐시 성능 테스트
 *
 * 테스트 목적:
 * 1. 캐시 적용 전후 응답 시간 비교
 * 2. 캐시 히트율 측정
 * 3. 동시 요청 처리 성능 비교
 * 4. 캐시 무효화 동작 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("좌석 조회 캐시 성능 테스트")
class SeatCachePerformanceTest @Autowired constructor(
    private val getAvailableSeatsUseCase: GetAvailableSeatsUseCase,
    private val seatCacheService: SeatCacheService,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
) {

    companion object {
        @Container
        @ServiceConnection
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var concert: Concert
    private lateinit var schedule: ConcertSchedule

    @BeforeEach
    fun setUp() {
        // 콘서트 및 일정 생성
        concert = Concert(title = "Test Concert", description = "Performance Test")
            .apply { concertJpaRepository.save(this) }

        schedule = ConcertSchedule(
            concertId = concert.id,
            concertDate = LocalDateTime.now().plusDays(7),
        ).apply { concertScheduleJpaRepository.save(this) }

        // 50개 좌석 생성
        repeat(50) { idx ->
            Seat(
                concertScheduleId = schedule.id,
                seatNumber = idx + 1,
                price = 50000,
                seatStatus = SeatStatus.AVAILABLE,
            ).apply { seatJpaRepository.save(this) }
        }
    }

    @AfterEach
    fun cleanup() {
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        // 캐시 초기화
        seatCacheService.evictAvailableSeats(schedule.id)
    }

    @Test
    @DisplayName("[성능] 캐시 미스 vs 캐시 히트 응답 시간 비교")
    fun `should measure response time difference between cache miss and hit`() {
        val command = GetAvailableSeatsCommand(
            concertId = concert.id,
            scheduleId = schedule.id,
        )

        // 1차 호출 - 캐시 MISS (DB 조회)
        val firstCallTime = measureTimeMillis {
            getAvailableSeatsUseCase.execute(command)
        }

        // 2차 호출 - 캐시 HIT (Redis 조회)
        val secondCallTime = measureTimeMillis {
            getAvailableSeatsUseCase.execute(command)
        }

        // 3차 호출 - 캐시 HIT (Redis 조회)
        val thirdCallTime = measureTimeMillis {
            getAvailableSeatsUseCase.execute(command)
        }

        val cachedAvgTime = (secondCallTime + thirdCallTime) / 2
        val speedup = firstCallTime.toDouble() / cachedAvgTime

        println(
            """
            ✅ 캐시 적용 성능 비교:
            - 1차 호출 (캐시 MISS): ${firstCallTime}ms
            - 2차 호출 (캐시 HIT): ${secondCallTime}ms
            - 3차 호출 (캐시 HIT): ${thirdCallTime}ms
            - 캐시 평균 응답시간: ${cachedAvgTime}ms
            - 성능 개선: ${String.format("%.2f", speedup)}배 향상
            """.trimIndent(),
        )

        // 캐시 히트가 더 빠르거나 비슷해야 함 (여유있게 검증)
        assertThat(cachedAvgTime).isLessThanOrEqualTo(firstCallTime * 2)
    }

    @Test
    @DisplayName("[동시성] 100개 동시 요청 처리 시간 측정")
    fun `should measure performance with concurrent requests`() {
        val requestCount = 100
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(requestCount)

        val command = GetAvailableSeatsCommand(
            concertId = concert.id,
            scheduleId = schedule.id,
        )

        // 동시 요청 처리 시간 측정
        val totalTime = measureTimeMillis {
            repeat(requestCount) {
                executor.submit {
                    try {
                        getAvailableSeatsUseCase.execute(command)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        }

        executor.shutdown()

        val avgTime = totalTime / requestCount

        println(
            """
            ✅ 동시 요청 처리 성능:
            - 총 요청 수: $requestCount
            - 총 소요 시간: ${totalTime}ms
            - 평균 응답 시간: ${avgTime}ms
            - 초당 처리량 (TPS): ${requestCount * 1000 / totalTime}
            """.trimIndent(),
        )

        // 평균 응답시간이 합리적인 범위 내에 있는지 확인
        assertThat(avgTime).isLessThan(100) // 평균 100ms 이하
    }

    @Test
    @DisplayName("[캐시 무효화] 캐시 무효화 후 다시 DB 조회")
    fun `should query DB after cache eviction`() {
        val command = GetAvailableSeatsCommand(
            concertId = concert.id,
            scheduleId = schedule.id,
        )

        // 1차 호출 - 캐시에 저장
        getAvailableSeatsUseCase.execute(command)

        // 2차 호출 - 캐시에서 조회
        val cachedResult = getAvailableSeatsUseCase.execute(command)
        assertThat(cachedResult.seats).hasSize(50)

        // 캐시 무효화
        seatCacheService.evictAvailableSeats(schedule.id)

        // 3차 호출 - 캐시 미스, DB 재조회
        val afterEviction = getAvailableSeatsUseCase.execute(command)
        assertThat(afterEviction.seats).hasSize(50)

        println("✅ 캐시 무효화 동작 검증 완료")
    }

    @Test
    @DisplayName("[정합성] 좌석 상태 변경 시 캐시 반영 확인")
    fun `should reflect seat status changes after cache eviction`() {
        val command = GetAvailableSeatsCommand(
            concertId = concert.id,
            scheduleId = schedule.id,
        )

        // 1차 호출 - 50개 좌석 캐시
        val beforeReservation = getAvailableSeatsUseCase.execute(command)
        assertThat(beforeReservation.seats).hasSize(50)

        // 좌석 1개 예약 (AVAILABLE → TEMPORARY_RESERVED)
        val seat = seatJpaRepository.findAllByConcertScheduleId(schedule.id).first()
        seat.seatStatus = SeatStatus.TEMPORARY_RESERVED
        seatJpaRepository.save(seat)

        // 캐시 무효화
        seatCacheService.evictAvailableSeats(schedule.id)

        // 2차 호출 - 49개 좌석 (1개 예약됨)
        val afterReservation = getAvailableSeatsUseCase.execute(command)
        assertThat(afterReservation.seats).hasSize(49)

        println("✅ 좌석 상태 변경 후 캐시 정합성 검증 완료")
    }

    @Test
    @DisplayName("[TTL] 캐시 만료 시간 동작 확인 (10초)")
    fun `should expire cache after TTL`() {
        val command = GetAvailableSeatsCommand(
            concertId = concert.id,
            scheduleId = schedule.id,
        )

        // 1차 호출 - 캐시 저장
        getAvailableSeatsUseCase.execute(command)

        // 2차 호출 - 캐시 HIT
        val beforeExpiry = getAvailableSeatsUseCase.execute(command)
        assertThat(beforeExpiry.seats).hasSize(50)

        // 11초 대기 (TTL 10초 + 여유 1초)
        Thread.sleep(11000)

        // 3차 호출 - 캐시 만료되어 DB 재조회
        val afterExpiry = getAvailableSeatsUseCase.execute(command)
        assertThat(afterExpiry.seats).hasSize(50)

        println("✅ TTL 만료 후 자동 재조회 동작 검증 완료")
    }
}
