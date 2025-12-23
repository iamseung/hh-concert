package kr.hhplus.be.server.infrastructure.client

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.retry.RetryOperator
import io.github.resilience4j.retry.Retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * 외부 API 통신 인프라 계층
 *
 * 책임:
 * - 순수 HTTP 통신 처리 (POST, GET 등)
 * - Resilience4j 패턴 적용 (Retry + CircuitBreaker)
 * - 타임아웃 관리
 *
 * 특징:
 * - 도메인 로직 무관 (범용 HTTP 클라이언트)
 * - 재사용 가능 (모든 외부 API 호출에서 사용 가능)
 * - 테스트 용이성 (Mock 교체 가능)
 */
@Component
class ExternalApiSender(
    @Qualifier("externalApiWebClient")
    private val webClient: WebClient,

    @Qualifier("dataPlatformRetry")
    private val retry: Retry,

    @Qualifier("dataPlatformCircuitBreaker")
    private val circuitBreaker: CircuitBreaker,
) {

    /**
     * POST 요청 전송
     *
     * @param uri 요청 URI (baseUrl 기준 상대 경로 또는 절대 URL)
     * @param headers 요청 헤더 (Map 형태)
     * @param body 요청 본문 (Any 타입, JSON 직렬화 가능한 객체)
     * @param timeoutSeconds 타임아웃 (초 단위, 기본값 3초)
     * @return Mono<String> 응답 본문
     *
     * 적용 패턴:
     * - Retry: 실패 시 자동 재시도 (지수 백오프)
     * - CircuitBreaker: 연속 실패 시 회로 차단
     * - Timeout: 지정된 시간 내 응답 없으면 실패
     */
    fun post(
        uri: String,
        headers: Map<String, String> = emptyMap(),
        body: Any,
        timeoutSeconds: Long = 3,
    ): Mono<String> {
        return webClient.post()
            .uri(uri)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    }
}
