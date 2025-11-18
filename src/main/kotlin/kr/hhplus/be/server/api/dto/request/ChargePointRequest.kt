package kr.hhplus.be.server.api.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "포인트 충전 요청")
data class ChargePointRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @Schema(description = "충전할 포인트 금액 (원)", example = "100000", minimum = "1")
    val amount: Int,
)