package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetAvailableSeatsUseCase(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
    private val seatService: SeatService,
) {

    @Transactional(readOnly = true)
    fun execute(command: GetAvailableSeatsCommand): GetAvailableSeatsResult {
        // 1. 콘서트 검증
        val concert = concertService.findById(command.concertId)

        // 2. 일정 검증
        val schedule = concertScheduleService.findById(command.scheduleId)
        schedule.validateIsConcert(concert)

        // 3. 예약 가능한 좌석 조회 (DB에서 직접 필터링)
        val availableSeats = seatService.findAvailableSeatsByConcertScheduleId(command.scheduleId)

        // 4. 결과 반환
        return GetAvailableSeatsResult(
            seats = availableSeats.map { seat ->
                GetAvailableSeatsResult.SeatInfo(
                    seatId = seat.id,
                    concertScheduleId = seat.concertScheduleId,
                    seatNumber = seat.seatNumber,
                    price = seat.price,
                    status = seat.seatStatus,
                )
            },
        )
    }
}
