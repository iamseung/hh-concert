package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueToken

@Schema(description = "대기 상태 응답")
data class QueueStatusResponse(
    @Schema(description = "현재 대기 순서 (0이면 ACTIVE 상태)", example = "42")
    val queuePosition: Int,

    @Schema(description = "대기열 상태", example = "WAITING")
    val queueStatus: QueueStatus,

    @Schema(description = "예상 대기 시간 (분)", example = "15")
    val estimatedWaitTimeMinutes: Int,
) {
    companion object {
        private const val MINUTES_PER_USER = 1

        fun from(queueToken: QueueToken): QueueStatusResponse {
            val estimatedTime = if (queueToken.queueStatus == QueueStatus.WAITING) {
                queueToken.queuePosition * MINUTES_PER_USER
            } else {
                0
            }

            return QueueStatusResponse(
                queuePosition = queueToken.queuePosition,
                queueStatus = queueToken.queueStatus,
                estimatedWaitTimeMinutes = estimatedTime,
            )
        }
    }
}
