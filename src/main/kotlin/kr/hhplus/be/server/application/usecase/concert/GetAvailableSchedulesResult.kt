package kr.hhplus.be.server.application.usecase.concert

import java.time.LocalDateTime

data class GetAvailableSchedulesResult(
    val schedules: List<ScheduleInfo>,
) {
    data class ScheduleInfo(
        val scheduleId: Long,
        val concertId: Long,
        val concertDate: LocalDateTime,
    )
}
