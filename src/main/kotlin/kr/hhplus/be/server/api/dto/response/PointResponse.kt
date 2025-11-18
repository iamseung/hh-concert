package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.point.model.Point

@Schema(description = "포인트 응답")
data class PointResponse(
    @Schema(description = "포인트 ID", example = "1")
    val id: Long?,
    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,
    @Schema(description = "현재 포인트 잔액 (원)", example = "500000")
    val balance: Int,
) {

    companion object {
        fun from(point: Point): PointResponse {
            return PointResponse(
                id = point.id,
                userId = point.userId,
                balance = point.balance,
            )
        }
    }
}