package kr.hhplus.be.server.api.dto.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 대기열 토큰 발급 요청
 */
@Schema(description = "대기열 토큰 발급 요청")
data class IssueQueueTokenRequest(
    @Schema(description = "사용자 ID", example = "1", required = true)
    val userId: Long,
)