package kr.hhplus.be.server.common.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답")
data class ErrorResponse(
    @Schema(description = "타임스탬프", example = "2025-01-01T00:00:00Z")
    val timestamp: String,
    @Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @Schema(description = "에러명", example = "Bad Request")
    val error: String,
    @Schema(description = "에러 메시지", example = "잘못된 요청입니다")
    val message: String,
    @Schema(description = "요청 경로", example = "/api/v1/concerts/1/schedules")
    val path: String,
)
