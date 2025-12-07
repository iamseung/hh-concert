package kr.hhplus.be.server.api.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import java.time.format.DateTimeFormatter

@Schema(description = "콘서트 일정 응답")
data class ConcertScheduleResponse(
    @Schema(description = "일정 ID", example = "1")
    val id: Long?,
    @Schema(description = "콘서트 ID", example = "1")
    val concertId: Long,
    @Schema(description = "콘서트 예약 가능한 날짜와 시간", example = "2025-06-15T19:00:00")
    val concertDate: String,
) {

    companion object {
        private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun from(schedule: ConcertScheduleModel): ConcertScheduleResponse {
            return ConcertScheduleResponse(
                id = schedule.id,
                concertId = schedule.concertId,
                concertDate = schedule.concertDate.format(dateTimeFormatter),
            )
        }
    }
}
