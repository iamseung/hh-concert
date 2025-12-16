package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.infrastructure.cache.ConcertScheduleCacheService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetAvailableSchedulesUseCase(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
    private val concertScheduleCacheService: ConcertScheduleCacheService,
) {

    @Transactional(readOnly = true)
    fun execute(command: GetAvailableSchedulesCommand): GetAvailableSchedulesResult {
        // 캐시에서 조회 시도
        concertScheduleCacheService.getSchedules(command.concertId)?.let { return it }

        // 1. 콘서트 검증
        val concert = concertService.findById(command.concertId)

        // 2. 예약 가능한 일정 조회
        val availableSchedules = concertScheduleService.findByConcertId(concert.id)
            .filter { schedule -> schedule.isAvailable }

        // 3. 결과 생성
        val result = GetAvailableSchedulesResult(
            schedules = availableSchedules.map { schedule ->
                GetAvailableSchedulesResult.ScheduleInfo(
                    scheduleId = schedule.id,
                    concertId = schedule.concertId,
                    concertDate = schedule.concertDate,
                )
            },
        )

        // 4. 캐시에 저장 (TTL: 30분)
        concertScheduleCacheService.setSchedules(command.concertId, result)

        return result
    }
}
