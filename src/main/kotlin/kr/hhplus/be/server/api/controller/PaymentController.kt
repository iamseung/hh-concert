package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.ProcessPaymentRequest
import kr.hhplus.be.server.api.dto.response.PaymentResponse
import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentCommand
import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentUseCase
import kr.hhplus.be.server.domain.payment.model.PaymentStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "결제 처리")
class PaymentController(
    private val processPaymentUseCase: ProcessPaymentUseCase,
) {

    @Operation(
        summary = "결제 처리",
        description = "임시 예약된 좌석에 대해 결제를 처리합니다. 포인트를 차감하고 예약을 확정합니다.",
        operationId = "processPayment",
    )
    @PostMapping
    fun processPayment(
        @Parameter(description = "사용자 ID", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("User-Id") userId: Long,
        @Parameter(description = "대기열 토큰 (UUID 형식)", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: ProcessPaymentRequest,
    ): PaymentResponse {
        val command = ProcessPaymentCommand(
            userId = userId,
            reservationId = request.reservationId,
            queueToken = queueToken,
        )
        val result = processPaymentUseCase.execute(command)
        return PaymentResponse(
            paymentId = result.paymentId,
            reservationId = result.reservationId,
            userId = result.userId,
            amount = result.amount,
            paymentStatus = PaymentStatus.PENDING,
        )
    }
}
