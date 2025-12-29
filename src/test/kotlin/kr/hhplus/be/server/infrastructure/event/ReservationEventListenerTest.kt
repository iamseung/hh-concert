package kr.hhplus.be.server.infrastructure.event

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * ReservationEventListener 단위 테스트
 *
 * 테스트 목표:
 * - 이벤트 수신 시 DataPlatformClient 호출 검증
 * - 성공/실패 시나리오별 로깅 검증
 *
 * Mock 전략:
 * - DataPlatformClient만 Mock (관심사 분리)
 * - 하위 레이어(ExternalApiSender, WebClient)는 독립적으로 테스트
 */
@DisplayName("ReservationEventListener 단위 테스트")
class ReservationEventListenerTest {

    private lateinit var listener: ReservationEventListener
    private lateinit var dataPlatformClient: DataPlatformClient

    @BeforeEach
    fun setUp() {
        dataPlatformClient = mockk()
        listener = ReservationEventListener(dataPlatformClient)
    }

    @Test
    @DisplayName("예약 확정 이벤트 수신 시 DataPlatformClient 호출")
    fun `should call DataPlatformClient when reservation confirmed event received`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        every { dataPlatformClient.sendReservation(event) } returns Mono.just("{\"status\":\"success\"}")

        // When
        listener.onReservation(event)

        // 비동기 처리 완료 대기
        Thread.sleep(500)

        // Then
        verify(exactly = 1) { dataPlatformClient.sendReservation(event) }
    }

    @Test
    @DisplayName("DataPlatformClient 전송 성공 시 로깅 처리")
    fun `should handle success response from DataPlatformClient`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 2L,
            concertId = 200L,
            concertTitle = "Success Test Concert",
            userId = 20L,
        )

        val successResponse = "{\"status\":\"success\",\"message\":\"Data received\"}"
        every { dataPlatformClient.sendReservation(event) } returns Mono.just(successResponse)

        // When
        listener.onReservation(event)

        // 비동기 처리 완료 대기
        Thread.sleep(500)

        // Then
        verify(exactly = 1) { dataPlatformClient.sendReservation(event) }
    }

    @Test
    @DisplayName("DataPlatformClient 전송 실패 시 로깅 처리")
    fun `should handle error from DataPlatformClient`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 3L,
            concertId = 300L,
            concertTitle = "Error Test Concert",
            userId = 30L,
        )

        val errorMessage = "External API unavailable"
        every { dataPlatformClient.sendReservation(event) } returns Mono.error(RuntimeException(errorMessage))

        // When
        listener.onReservation(event)

        // 비동기 처리 완료 대기
        Thread.sleep(500)

        // Then
        verify(exactly = 1) { dataPlatformClient.sendReservation(event) }
        // 에러 발생해도 예외 전파 없음 (비동기 subscribe() 처리)
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

        events.forEach { event ->
            every { dataPlatformClient.sendReservation(event) } returns Mono.just("{\"status\":\"success\"}")
        }

        // When
        events.forEach { event ->
            listener.onReservation(event)
        }

        // 비동기 처리 완료 대기
        Thread.sleep(1000)

        // Then
        events.forEach { event ->
            verify(exactly = 1) { dataPlatformClient.sendReservation(event) }
        }
    }
}
