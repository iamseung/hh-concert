package kr.hhplus.be.server.infrastructure.client

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * 데이터 플랫폼 API 클라이언트
 *
 * 책임:
 * - 데이터 플랫폼 도메인 특화 API 제공
 * - 비즈니스 이벤트 → API 페이로드 변환
 * - 멱등성 키 생성 및 관리
 *
 * 특징:
 * - 도메인 로직 포함 (예약, 주문 등)
 * - ExternalApiSender를 활용한 실제 통신
 * - 비즈니스 데이터 중심 인터페이스
 */
@Component
class DataPlatformClient(
    private val externalApiSender: ExternalApiSender,
    @Value("\${data-platform.base-url:http://localhost:8080}")
    private val baseUrl: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 확정 정보를 데이터 플랫폼으로 전송
     *
     * @param event 예약 확정 이벤트
     * @return Mono<String> 응답 결과
     *
     * 처리 흐름:
     * 1. 멱등성 키 생성 (reservationId + timestamp)
     * 2. 페이로드 변환 (이벤트 → API 요청 형식)
     * 3. ExternalApiSender를 통한 HTTP POST 전송
     */
    fun sendReservation(event: ReservationConfirmedEvent): Mono<String> {
        val idempotencyKey = generateIdempotencyKey("reservation", event.reservationId)
        val payload = createReservationPayload(event)
        val headers = mapOf("X-Idempotency-Key" to idempotencyKey)

        logger.info(
            "데이터 플랫폼 예약 전송 시작 - reservationId={}, idempotencyKey={}",
            event.reservationId,
            idempotencyKey,
        )

        return externalApiSender.post(
            uri = "$baseUrl/api/mock/reservation",
            headers = headers,
            body = payload,
        )
    }

    /**
     * 멱등성 키 생성
     *
     * 형식: {prefix}-{id}-{timestamp}
     * 예시: reservation-123-1234567890
     */
    private fun generateIdempotencyKey(prefix: String, id: Long): String {
        return "$prefix-$id-${System.currentTimeMillis()}"
    }

    /**
     * 예약 이벤트를 API 페이로드로 변환
     */
    private fun createReservationPayload(event: ReservationConfirmedEvent): Map<String, Any> {
        return mapOf(
            "eventType" to "RESERVATION_CONFIRMED",
            "reservationId" to event.reservationId,
            "concertId" to event.concertId,
            "concertTitle" to event.concertTitle,
            "userId" to event.userId,
            "timestamp" to System.currentTimeMillis(),
        )
    }
}
