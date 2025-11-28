package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetAvailableSchedulesUseCase(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
) {

    @Transactional(readOnly = true)
    fun execute(command: GetAvailableSchedulesCommand): GetAvailableSchedulesResult {
        // 1. 콘서트 검증
        val concert = concertService.findById(command.concertId)

        // 2. 예약 가능한 일정 조회
        val availableSchedules = concertScheduleService.findByConcertId(concert.id)
            .filter { schedule -> schedule.isAvailable }

        // 3. 결과 반환
        return GetAvailableSchedulesResult(
            schedules = availableSchedules.map { schedule ->
                GetAvailableSchedulesResult.ScheduleInfo(
                    scheduleId = schedule.id,
                    concertId = schedule.concertId,
                    concertDate = schedule.concertDate
                )
            }
        )
    }
}
