package kr.hhplus.be.server.api.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "예약 생성 요청")
data class CreateReservationRequest(
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @Schema(description = "콘서트 일정 ID", example = "1")
    val scheduleId: Long,
    @Schema(description = "좌석 ID", example = "15")
    val seatId: Long,
)
