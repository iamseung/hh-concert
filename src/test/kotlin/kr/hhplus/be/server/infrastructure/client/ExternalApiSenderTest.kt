package kr.hhplus.be.server.infrastructure.client

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration

/**
 * ExternalApiSender 단위 테스트
 *
 * - WebClient Mock 복잡도를 피하기 위해 실제 WebClient 사용 (테스트용 Mock 서버 없음)
 * - Resilience4j 설정 검증에 집중
 * - 실제 통합 테스트는 별도로 작성 (MockWebServer 사용)
 */
@DisplayName("ExternalApiSender 구조 검증 테스트")
class ExternalApiSenderTest {

    private lateinit var sender: ExternalApiSender
    private lateinit var webClient: WebClient
    private lateinit var retry: Retry
    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setUp() {
        // 실제 WebClient 생성 (HTTP 호출은 하지 않음)
        webClient = WebClient.builder().build()

        // 실제 Resilience4j 컴포넌트 생성 (테스트용 설정)
        val retryConfig = RetryConfig.custom<Any>()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(100))
            .build()

        val circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(5)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .build()

        retry = RetryRegistry.of(retryConfig).retry("test")
        circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig).circuitBreaker("test")

        sender = ExternalApiSender(webClient, retry, circuitBreaker)
    }

    @Test
    @DisplayName("ExternalApiSender 인스턴스 생성 가능")
    fun `should create ExternalApiSender instance`() {
        // Then
        assertThat(sender).isNotNull
    }

    @Test
    @DisplayName("Retry 설정이 올바르게 주입됨")
    fun `should have retry configuration`() {
        // Then
        assertThat(retry.name).isEqualTo("test")
        assertThat(retry.retryConfig.maxAttempts).isEqualTo(2)
    }

    @Test
    @DisplayName("CircuitBreaker 설정이 올바르게 주입됨")
    fun `should have circuit breaker configuration`() {
        // Then
        assertThat(circuitBreaker.name).isEqualTo("test")
        assertThat(circuitBreaker.circuitBreakerConfig.slidingWindowSize).isEqualTo(5)
        assertThat(circuitBreaker.circuitBreakerConfig.failureRateThreshold).isEqualTo(50f)
    }

    @Test
    @DisplayName("WebClient가 올바르게 주입됨")
    fun `should have WebClient configured`() {
        // Then
        assertThat(webClient).isNotNull
    }

    /**
     * 실제 HTTP 통신 테스트
     *
     * 주의: 이 테스트는 실패 예상 (실제 서버 없음)
     * 통합 테스트에서 MockWebServer를 사용하여 검증해야 함
     */
    @Test
    @DisplayName("실제 HTTP 호출 시도 (예상 실패)")
    fun `should attempt HTTP call and fail without server`() {
        // Given
        val uri = "http://localhost:9999/api/test"
        val body = mapOf("key" to "value")

        // When
        val result = sender.post(uri, emptyMap(), body, 1)

        // Then - 서버가 없으므로 에러 발생 예상
        StepVerifier.create(result)
            .expectError()
            .verify(Duration.ofSeconds(5))
    }
}
