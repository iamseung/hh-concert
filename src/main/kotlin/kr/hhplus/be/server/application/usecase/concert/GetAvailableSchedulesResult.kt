package kr.hhplus.be.server.application.usecase.concert

import java.time.LocalDate

data class GetAvailableSchedulesResult(
    val schedules: List<ScheduleInfo>
) {
    data class ScheduleInfo(
        val scheduleId: Long,
        val concertId: Long,
        val concertDate: LocalDate
    )
}
