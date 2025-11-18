package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.ProcessPaymentRequest
import kr.hhplus.be.server.api.dto.response.PaymentResponse
import kr.hhplus.be.server.application.PaymentFacade
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "결제 처리")
class PaymentController(
    private val paymentFacade: PaymentFacade,
) {

    @Operation(
        summary = "결제 처리",
        description = "임시 예약된 좌석에 대해 결제를 처리합니다. 포인트를 차감하고 예약을 확정합니다.",
        operationId = "processPayment",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "결제 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PaymentResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (예약 상태가 TEMPORARY가 아님, 포인트 부족 등)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "예약 또는 사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    fun processPayment(
        @Parameter(description = "사용자 ID", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("User-Id") userId: Long,
        @Parameter(description = "대기열 토큰 (UUID 형식)", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: ProcessPaymentRequest,
    ): PaymentResponse {
        return paymentFacade.processPayment(userId, request.reservationId, queueToken)
    }
}
