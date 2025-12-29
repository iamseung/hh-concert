package kr.hhplus.be.server.presentation.mock

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mock 외부 API Controller
 *
 * 외부 API 호출 테스트를 위한 Mock Endpoint
 * 실제 외부 서비스 없이 WebClient 동작을 확인할 수 있음
 */
@RestController
@RequestMapping("/api/mock")
class MockExternalApiController {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/reservation")
    fun receiveReservation(
        @RequestHeader(value = "X-Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody payload: Map<String, Any>,
    ): Map<String, Any> {
        logger.info("Mock API called - Idempotency-Key: {}, Payload: {}", idempotencyKey, payload)

        return mapOf(
            "status" to "success",
            "message" to "Reservation received",
            "receivedData" to payload,
        )
    }
}
