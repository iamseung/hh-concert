package kr.hhplus.be.server.infrastructure.configuration

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * 외부 API 호출을 위한 WebClient 설정
 *
 * Resilience4j의 Retry 및 CircuitBreaker와 함께 사용하여
 * 외부 API 호출 시 안정성과 장애 격리를 제공합니다.
 */
@Configuration
class WebClientConfig(
    @Value("\${external-api.timeout-seconds}")
    private val timeoutSeconds: Long,

    private val retryRegistry: RetryRegistry,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {

    /**
     * 외부 API 호출용 WebClient 빈 생성
     *
     * 타임아웃 설정:
     * - CONNECT_TIMEOUT: 연결 시도 시 최대 대기 시간
     * - responseTimeout: 전체 응답 수신 최대 대기 시간
     * - ReadTimeoutHandler: 데이터 읽기 작업 시 최대 대기 시간
     * - WriteTimeoutHandler: 데이터 쓰기 작업 시 최대 대기 시간
     *
     * 모든 타임아웃은 application.yml의 external-api.timeout-seconds 값을 사용합니다.
     */
    @Bean
    fun externalApiWebClient(): WebClient {
        val httpClient = HttpClient.create()
            // TCP 연결 타임아웃 설정 (밀리초 단위)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds.toInt() * 1000)
            // HTTP 응답 전체 타임아웃 설정
            .responseTimeout(Duration.ofSeconds(timeoutSeconds))
            // 읽기/쓰기 타임아웃 핸들러 추가
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                    .addHandlerLast(WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    /**
     * 데이터 플랫폼 API 호출용 Retry 정책 빈
     *
     * application.yml에 정의된 "dataPlatform" Retry 설정을 사용합니다.
     * 일시적인 네트워크 오류나 서버 장애 시 자동으로 재시도합니다.
     */
    @Bean
    fun dataPlatformRetry() = retryRegistry.retry("dataPlatform")

    /**
     * 데이터 플랫폼 API 호출용 CircuitBreaker 빈
     *
     * application.yml에 정의된 "dataPlatform" CircuitBreaker 설정을 사용합니다.
     * 연속적인 실패 발생 시 회로를 차단하여 장애 전파를 방지합니다.
     */
    @Bean
    fun dataPlatformCircuitBreaker() = circuitBreakerRegistry.circuitBreaker("dataPlatform")

    /**
     * Discord 알림용 WebClient 빈
     *
     * DiscordNotifier에서 사용하는 간단한 WebClient입니다.
     * 타임아웃은 동일하게 설정합니다.
     */
    @Bean
    fun webClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds.toInt() * 1000)
            .responseTimeout(Duration.ofSeconds(timeoutSeconds))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                    .addHandlerLast(WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
