package kr.hhplus.be.server.infrastructure.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * DataPlatformClient 단위 테스트
 *
 * 테스트 목표:
 * - 도메인 이벤트 → API 페이로드 변환 검증
 * - 멱등성 키 생성 검증
 * - ExternalApiSender 호출 검증
 *
 * Mock 전략:
 * - ExternalApiSender만 Mock
 * - 페이로드 변환 로직은 실제 실행
 */
@DisplayName("DataPlatformClient 단위 테스트")
class DataPlatformClientTest {

    private lateinit var client: DataPlatformClient
    private lateinit var externalApiSender: ExternalApiSender
    private val baseUrl = "http://test-platform:8080"

    @BeforeEach
    fun setUp() {
        externalApiSender = mockk()
        client = DataPlatformClient(externalApiSender, baseUrl)
    }

    @Test
    @DisplayName("예약 이벤트 전송 시 올바른 URI로 호출")
    fun `should call ExternalApiSender with correct URI`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val uriSlot = slot<String>()
        val headersSlot = slot<Map<String, String>>()
        val bodySlot = slot<Any>()

        every {
            externalApiSender.post(
                uri = capture(uriSlot),
                headers = capture(headersSlot),
                body = capture(bodySlot),
            )
        } returns Mono.just("{\"status\":\"success\"}")

        // When
        val result = client.sendReservation(event)

        // Then
        StepVerifier.create(result)
            .expectNext("{\"status\":\"success\"}")
            .verifyComplete()

        assertThat(uriSlot.captured).isEqualTo("$baseUrl/api/mock/reservation")
        verify(exactly = 1) { externalApiSender.post(any(), any(), any()) }
    }

    @Test
    @DisplayName("멱등성 키가 헤더에 포함되어 전송")
    fun `should include idempotency key in headers`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val headersSlot = slot<Map<String, String>>()

        every {
            externalApiSender.post(
                uri = any(),
                headers = capture(headersSlot),
                body = any(),
            )
        } returns Mono.just("{\"status\":\"success\"}")

        // When
        client.sendReservation(event).subscribe()

        // Then
        val capturedHeaders = headersSlot.captured
        assertThat(capturedHeaders).containsKey("X-Idempotency-Key")
        assertThat(capturedHeaders["X-Idempotency-Key"]).startsWith("reservation-1-")
    }

    @Test
    @DisplayName("페이로드에 필수 필드가 포함")
    fun `should include required fields in payload`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 123L,
            concertId = 456L,
            concertTitle = "Amazing Concert",
            userId = 789L,
        )

        val bodySlot = slot<Any>()

        every {
            externalApiSender.post(
                uri = any(),
                headers = any(),
                body = capture(bodySlot),
            )
        } returns Mono.just("{\"status\":\"success\"}")

        // When
        client.sendReservation(event).subscribe()

        // Then
        val capturedBody = bodySlot.captured as Map<*, *>

        assertThat(capturedBody["eventType"]).isEqualTo("RESERVATION_CONFIRMED")
        assertThat(capturedBody["reservationId"]).isEqualTo(123L)
        assertThat(capturedBody["concertId"]).isEqualTo(456L)
        assertThat(capturedBody["concertTitle"]).isEqualTo("Amazing Concert")
        assertThat(capturedBody["userId"]).isEqualTo(789L)
    }

    @Test
    @DisplayName("ExternalApiSender 에러 시 Mono.error 전파")
    fun `should propagate error from ExternalApiSender`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val errorMessage = "Network error"
        every {
            externalApiSender.post(any(), any(), any())
        } returns Mono.error(RuntimeException(errorMessage))

        // When
        val result = client.sendReservation(event)

        // Then
        StepVerifier.create(result)
            .expectErrorMatches { it is RuntimeException && it.message == errorMessage }
            .verify()
    }

    @Test
    @DisplayName("동일 reservationId로 여러 번 호출 시 다른 멱등성 키 생성")
    fun `should generate different idempotency keys for same reservationId`() {
        // Given
        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val headersList = mutableListOf<Map<String, String>>()

        every {
            externalApiSender.post(
                uri = any(),
                headers = capture(headersList),
                body = any(),
            )
        } returns Mono.just("{\"status\":\"success\"}")

        // When
        client.sendReservation(event).subscribe()
        Thread.sleep(10) // timestamp 차이를 위한 대기
        client.sendReservation(event).subscribe()

        // Then
        assertThat(headersList).hasSize(2)
        val key1 = headersList[0]["X-Idempotency-Key"]
        val key2 = headersList[1]["X-Idempotency-Key"]

        assertThat(key1).isNotEqualTo(key2)
        assertThat(key1).startsWith("reservation-1-")
        assertThat(key2).startsWith("reservation-1-")
    }

    @Test
    @DisplayName("baseUrl 설정이 올바르게 적용")
    fun `should use configured baseUrl`() {
        // Given
        val customBaseUrl = "http://custom-platform:9999"
        val customClient = DataPlatformClient(externalApiSender, customBaseUrl)

        val event = ReservationConfirmedEvent(
            reservationId = 1L,
            concertId = 100L,
            concertTitle = "Test Concert",
            userId = 10L,
        )

        val uriSlot = slot<String>()

        every {
            externalApiSender.post(
                uri = capture(uriSlot),
                headers = any(),
                body = any(),
            )
        } returns Mono.just("{\"status\":\"success\"}")

        // When
        customClient.sendReservation(event).subscribe()

        // Then
        assertThat(uriSlot.captured).isEqualTo("$customBaseUrl/api/mock/reservation")
    }
}
