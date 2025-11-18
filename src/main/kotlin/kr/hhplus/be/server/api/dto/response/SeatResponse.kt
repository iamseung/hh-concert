package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.model.SeatStatus

@Schema(description = "좌석 응답")
data class SeatResponse(
    @Schema(description = "좌석 ID", example = "1")
    val id: Long?,
    @Schema(description = "일정 ID", example = "1")
    val scheduleId: Long,
    @Schema(description = "좌석 번호 (1-50)", example = "15", minimum = "1", maximum = "50")
    val seatNumber: Int,
    @Schema(description = "좌석 상태", example = "AVAILABLE", allowableValues = ["AVAILABLE", "TEMPORARILY_RESERVED", "RESERVED"])
    val seatStatus: SeatStatus,
    @Schema(description = "좌석 가격 (원)", example = "150000")
    val price: Int,
) {
    companion object {
        fun from(seat: Seat): SeatResponse {
            return SeatResponse(
                id = seat.getId(),
                scheduleId = seat.concertScheduleId,
                seatNumber = seat.seatNumber,
                seatStatus = seat.seatStatus,
                price = seat.price,
            )
        }
    }
}
