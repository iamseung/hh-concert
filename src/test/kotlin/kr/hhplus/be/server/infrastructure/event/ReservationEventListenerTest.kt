package kr.hhplus.be.server.infrastructure.event

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

/**
 * ReservationEventListener 단위 테스트
 *
 * 테스트 목표:
 * - 이벤트 수신 시 KafkaTemplate 호출 검증
 * - Kafka 메시지 발행 성공 검증
 *
 * Mock 전략:
 * - KafkaTemplate만 Mock (관심사 분리)
 */
@DisplayName("ReservationEventListener 단위 테스트")
class ReservationEventListenerTest {

    private lateinit var listener: ReservationEventListener
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private val topic = "reservation.confirmed"

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk(relaxed = true)
        listener = ReservationEventListener(kafkaTemplate, topic)
    }

    @Test
    @DisplayName("예약 확정 이벤트 수신 시 Kafka로 메시지 발행")
    fun `should publish message to Kafka when reservation confirmed event received`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val future = CompletableFuture<SendResult<String, Any>>()
        every { kafkaTemplate.send(topic, event.reservationId.toString(), event) } returns future

        // When
        listener.onReservation(event)

        // 비동기 처리 완료 대기
        Thread.sleep(500)

        // Then
        verify(exactly = 1) { kafkaTemplate.send(topic, event.reservationId.toString(), event) }
    }

    @Test
    @DisplayName("여러 이벤트 동시 처리")
    fun `should handle multiple events concurrently`() {
        // Given
        val events = listOf(
            ReservationConfirmedEvent(1L, 100L, "Concert 1", 10L),
            ReservationConfirmedEvent(2L, 200L, "Concert 2", 20L),
            ReservationConfirmedEvent(3L, 300L, "Concert 3", 30L),
        )

        val future = CompletableFuture<SendResult<String, Any>>()
        events.forEach { event ->
            every { kafkaTemplate.send(topic, event.reservationId.toString(), event) } returns future
        }

        // When
        events.forEach { event ->
            listener.onReservation(event)
        }

        // 비동기 처리 완료 대기
        Thread.sleep(1000)

        // Then
        events.forEach { event ->
            verify(exactly = 1) { kafkaTemplate.send(topic, event.reservationId.toString(), event) }
        }
    }
}
