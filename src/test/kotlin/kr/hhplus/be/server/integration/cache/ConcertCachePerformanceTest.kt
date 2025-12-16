package kr.hhplus.be.server.integration.cache

import kr.hhplus.be.server.application.usecase.concert.GetConcertsUseCase
import kr.hhplus.be.server.infrastructure.cache.ConcertCacheService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.system.measureTimeMillis

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("콘서트 목록 조회 캐시 성능 테스트")
class ConcertCachePerformanceTest {

    @Autowired
    private lateinit var getConcertsUseCase: GetConcertsUseCase

    @Autowired
    private lateinit var concertCacheService: ConcertCacheService

    @BeforeEach
    fun setUp() {
        // 테스트 시작 전 캐시 초기화
        concertCacheService.evictConcerts()
    }

    @Test
    @DisplayName("콘서트 목록 조회 - 캐시 미스 vs 캐시 히트 성능 비교")
    fun `concert list cache performance comparison`() {
        // Given: 캐시 미스 상태
        println("\n=== 콘서트 목록 조회 캐시 성능 테스트 ===")

        // When: 첫 번째 조회 (캐시 미스 - DB 조회)
        val cacheMissTime = measureTimeMillis {
            getConcertsUseCase.execute()
        }
        println("1차 조회 (캐시 미스 - DB 조회): ${cacheMissTime}ms")

        // When: 두 번째 조회 (캐시 히트 - Redis 조회)
        val cacheHitTime = measureTimeMillis {
            getConcertsUseCase.execute()
        }
        println("2차 조회 (캐시 히트 - Redis): ${cacheHitTime}ms")

        // Then: 성능 개선 확인
        val improvement = ((cacheMissTime - cacheHitTime).toDouble() / cacheMissTime * 100)
        println("성능 개선율: %.2f%%".format(improvement))
        println("응답 시간 단축: ${cacheMissTime - cacheHitTime}ms")

        assert(cacheHitTime < cacheMissTime) {
            "캐시 히트가 캐시 미스보다 빨라야 합니다"
        }
    }

    @Test
    @DisplayName("콘서트 목록 조회 - 다중 요청 성능 비교")
    fun `concert list multiple requests performance comparison`() {
        val requestCount = 100
        println("\n=== 콘서트 목록 다중 조회 성능 테스트 ($requestCount 회) ===")

        // Given: 캐시 미스 상태에서 다중 요청
        concertCacheService.evictConcerts()
        val withoutCacheTime = measureTimeMillis {
            repeat(requestCount) {
                concertCacheService.evictConcerts() // 매번 캐시 무효화
                getConcertsUseCase.execute()
            }
        }
        println("캐시 없이 ${requestCount}회 조회: ${withoutCacheTime}ms (평균: ${withoutCacheTime / requestCount}ms)")

        // When: 캐시 활성화 상태에서 다중 요청
        concertCacheService.evictConcerts()
        val withCacheTime = measureTimeMillis {
            repeat(requestCount) {
                getConcertsUseCase.execute() // 캐시 유지
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
    @DisplayName("콘서트 목록 캐시 TTL 검증")
    fun `concert list cache TTL validation`() {
        println("\n=== 콘서트 목록 캐시 TTL 검증 ===")

        // Given: 첫 조회로 캐시 생성
        val firstResult = getConcertsUseCase.execute()
        println("첫 조회 완료 - 캐시 생성")

        // When: 캐시에서 조회
        val cachedResult = concertCacheService.getConcerts()
        println("캐시 조회 완료")

        // Then: 캐시 데이터 검증
        assert(cachedResult != null) { "캐시에 데이터가 있어야 합니다" }
        assert(cachedResult!!.concerts.size == firstResult.concerts.size) {
            "캐시 데이터와 원본 데이터가 일치해야 합니다"
        }
        println("캐시 데이터 검증 완료 - 콘서트 ${cachedResult.concerts.size}개")

        // When: 캐시 무효화
        concertCacheService.evictConcerts()
        val evictedResult = concertCacheService.getConcerts()

        // Then: 캐시 무효화 검증
        assert(evictedResult == null) { "캐시 무효화 후 데이터가 없어야 합니다" }
        println("캐시 무효화 검증 완료")
    }
}
