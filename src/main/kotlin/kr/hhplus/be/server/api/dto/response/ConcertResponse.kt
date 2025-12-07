package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "콘서트 응답")
data class ConcertResponse(
    @Schema(description = "콘서트 ID", example = "1")
    val id: Long,

    @Schema(description = "콘서트 제목", example = "아이유 콘서트")
    val title: String,

    @Schema(description = "콘서트 설명", example = "2024 아이유 콘서트 H.E.R")
    val description: String?,
)
