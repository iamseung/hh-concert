package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel

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

        fun from(queueTokenModel: QueueTokenModel): QueueStatusResponse {
            val estimatedTime = if (queueTokenModel.queueStatus == QueueStatus.WAITING) {
                queueTokenModel.queuePosition * MINUTES_PER_USER
            } else {
                0
            }

            return QueueStatusResponse(
                queuePosition = queueTokenModel.queuePosition,
                queueStatus = queueTokenModel.queueStatus,
                estimatedWaitTimeMinutes = estimatedTime,
            )
        }
    }
}
