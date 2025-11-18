package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken
import java.time.LocalDateTime

@Schema(description = "대기열 토큰 응답")
data class QueueTokenResponse(
    @Schema(description = "토큰 ID", example = "1")
    val id: Long?,

    @Schema(description = "사용자 ID", example = "1")
    val userId: Long,

    @Schema(description = "대기열 UUID 토큰", example = "550e8400-e29b-41d4-a716-446655440000")
    val token: String,

    @Schema(description = "대기열 상태", example = "WAITING")
    val queueStatus: QueueStatus,

    @Schema(description = "대기 순서", example = "42")
    val queuePosition: Int,

    @Schema(description = "활성화 시간 (ACTIVE 상태일 때)", example = "2025-01-01T12:10:00")
    val activatedAt: LocalDateTime?,

    @Schema(description = "토큰 만료 시간", example = "2025-01-01T13:10:00")
    val expiresAt: LocalDateTime?,

    @Schema(description = "생성 시간", example = "2025-01-01T00:00:00")
    val createdAt: LocalDateTime?,

    @Schema(description = "수정 시간", example = "2025-01-01T00:00:00")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(queueToken: QueueToken): QueueTokenResponse {
            return QueueTokenResponse(
                id = queueToken.id,
                userId = queueToken.userId,
                token = queueToken.token,
                queueStatus = queueToken.queueStatus,
                queuePosition = queueToken.queuePosition,
                activatedAt = queueToken.activatedAt,
                expiresAt = queueToken.expiresAt,
                createdAt = queueToken.createdAt,
                updatedAt = queueToken.updatedAt,
            )
        }
    }
}
