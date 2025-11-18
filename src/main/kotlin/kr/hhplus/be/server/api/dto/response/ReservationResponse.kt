package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.reservation.model.Reservation

@Schema(description = "예약 응답")
data class ReservationResponse(
    @Schema(description = "예약 ID", example = "1")
    val id: Long?,
    @Schema(description = "좌석 ID", example = "15")
    val seatId: Long,
    @Schema(description = "예약 상태", example = "TEMPORARY", allowableValues = ["TEMPORARY", "CONFIRMED", "EXPIRED", "CANCELLED"])
    val reservationStatus: String,
    @Schema(description = "임시 배정 시작 시간", example = "2025-01-01T12:00:00Z")
    val temporaryReservedAt: String,
    @Schema(description = "임시 배정 만료 시간 (5분 후)", example = "2025-01-01T12:05:00Z")
    val temporaryExpiresAt: String,
) {

    companion object {
        fun from(reservation: Reservation): ReservationResponse {
            return ReservationResponse(
                id = reservation.id,
                seatId = reservation.seatId,
                reservationStatus = reservation.reservationStatus.name,
                temporaryReservedAt = reservation.temporaryReservedAt.toString(),
                temporaryExpiresAt = reservation.temporaryExpiredAt.toString(),
            )
        }
    }
}