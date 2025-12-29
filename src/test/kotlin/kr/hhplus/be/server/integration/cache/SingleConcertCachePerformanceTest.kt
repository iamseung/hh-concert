package kr.hhplus.be.server.integration.cache

import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.infrastructure.cache.ConcertCacheService
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
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
import kotlin.system.measureTimeMillis

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("단일 콘서트 조회 캐시 성능 테스트")
class SingleConcertCachePerformanceTest @Autowired constructor(
    private val concertService: ConcertService,
    private val concertCacheService: ConcertCacheService,
    private val concertJpaRepository: ConcertJpaRepository,
) {

    companion object {
        @Container
        @ServiceConnection
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var concert1: Concert
    private lateinit var concert2: Concert

    @BeforeEach
    fun setUp() {
        // 콘서트 생성
        concert1 = Concert(title = "Test Concert 1", description = "Performance Test 1")
            .apply { concertJpaRepository.save(this) }

        concert2 = Concert(title = "Test Concert 2", description = "Performance Test 2")
            .apply { concertJpaRepository.save(this) }

        // 캐시 초기화
        concertCacheService.evictConcert(concert1.id)
        concertCacheService.evictConcert(concert2.id)
    }

    @AfterEach
    fun tearDown() {
        concertJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("단일 콘서트 조회 - 다중 요청 성능 비교")
    fun `single concert multiple requests performance comparison`() {
        val requestCount = 100
        println("\n=== 단일 콘서트 다중 조회 성능 테스트 ($requestCount 회) ===")

        // Given: 캐시 미스 상태에서 다중 요청
        concertCacheService.evictConcert(concert1.id)
        val withoutCacheTime = measureTimeMillis {
            repeat(requestCount) {
                concertCacheService.evictConcert(concert1.id) // 매번 캐시 무효화
                concertService.findById(concert1.id)
            }
        }
        println("캐시 없이 ${requestCount}회 조회: ${withoutCacheTime}ms (평균: ${withoutCacheTime / requestCount}ms)")

        // When: 캐시 활성화 상태에서 다중 요청
        concertCacheService.evictConcert(concert1.id)
        val withCacheTime = measureTimeMillis {
            repeat(requestCount) {
                concertService.findById(concert1.id) // 캐시 유지
            }
        }
        println("캐시 사용 ${requestCount}회 조회: ${withCacheTime}ms (평균: ${withCacheTime / requestCount}ms)")

        // Then: 대량 요청시 성능 개선 확인
        val totalImprovement = ((withoutCacheTime - withCacheTime).toDouble() / withoutCacheTime * 100)
        println("전체 성능 개선율: %.2f%%".format(totalImprovement))
        println("총 응답 시간 단축: ${withoutCacheTime - withCacheTime}ms")

        assert(withCacheTime < withoutCacheTime) {
            "캐시를 사용한 다중 요청이 더 빨라야 합니다"
        }
    }

    @Test
    @DisplayName("단일 콘서트 캐시 TTL 검증")
    fun `single concert cache TTL validation`() {
        println("\n=== 단일 콘서트 캐시 TTL 검증 ===")

        // Given: 첫 조회로 캐시 생성
        val firstResult = concertService.findById(concert1.id)
        println("첫 조회 완료 - 캐시 생성")

        // When: 캐시에서 조회
        val cachedResult = concertCacheService.getConcert(concert1.id)
        println("캐시 조회 완료")

        // Then: 캐시 데이터 검증
        assert(cachedResult != null) { "캐시에 데이터가 있어야 합니다" }
        assert(cachedResult!!.id == firstResult.id) {
            "캐시 데이터와 원본 데이터가 일치해야 합니다"
        }
        assert(cachedResult.title == firstResult.title) {
            "캐시 데이터의 제목이 일치해야 합니다"
        }
        println("캐시 데이터 검증 완료 - 콘서트: ${cachedResult.title}")

        // When: 캐시 무효화
        concertCacheService.evictConcert(concert1.id)
        val evictedResult = concertCacheService.getConcert(concert1.id)

        // Then: 캐시 무효화 검증
        assert(evictedResult == null) { "캐시 무효화 후 데이터가 없어야 합니다" }
        println("캐시 무효화 검증 완료")
    }

    @Test
    @DisplayName("서로 다른 콘서트는 독립적으로 캐시됨")
    fun `different concerts have independent caches`() {
        println("\n=== 콘서트별 독립 캐시 검증 ===")

        // Given: 두 콘서트 조회하여 캐시 생성
        concertCacheService.evictConcert(concert1.id)
        concertCacheService.evictConcert(concert2.id)

        concertService.findById(concert1.id)
        concertService.findById(concert2.id)
        println("두 콘서트 캐시 생성 완료")

        // When: 콘서트1 캐시만 무효화
        concertCacheService.evictConcert(concert1.id)

        val concert1Cache = concertCacheService.getConcert(concert1.id)
        val concert2Cache = concertCacheService.getConcert(concert2.id)

        // Then: 콘서트1 캐시는 없고, 콘서트2 캐시는 존재
        assert(concert1Cache == null) { "콘서트1 캐시는 무효화되어야 합니다" }
        assert(concert2Cache != null) { "콘서트2 캐시는 유지되어야 합니다" }
        println("독립 캐시 검증 완료 - 콘서트1: 무효화됨, 콘서트2: 유지됨")
    }
}
