package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.ConcertService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetAvailableSeatsUseCase(
    private val concertService: ConcertService,
    private val concertScheduleService: ConcertScheduleService,
    private val seatService: SeatService,
    private val seatCacheService: SeatCacheService,
) {

    /**
     * 예약 가능한 좌석 조회 (캐시 적용)
     *
     * 캐시 전략:
     * 1. Redis 캐시에서 먼저 조회 (Cache Hit 시 즉시 반환)
     * 2. 캐시 미스 시 DB 조회
     * 3. DB 조회 결과를 캐시에 저장 (TTL 10초)
     *
     * 성능 개선:
     * - 동일 일정의 좌석을 다수 사용자가 조회할 때 DB 부하 감소
     * - 응답 시간 단축 (DB 조회 → Redis 조회)
     */
    @Transactional(readOnly = true)
    fun execute(command: GetAvailableSeatsCommand): GetAvailableSeatsResult {
        // 1. 콘서트 검증
        val concert = concertService.findById(command.concertId)

        // 2. 일정 검증
        val schedule = concertScheduleService.findById(command.scheduleId)
        schedule.validateIsConcert(concert)

        // 3. 캐시에서 좌석 조회 시도
        val cachedSeats = seatCacheService.getAvailableSeats(command.scheduleId)
        val availableSeats = if (cachedSeats != null) {
            // 캐시 HIT - Redis에서 조회된 데이터 사용
            cachedSeats
        } else {
            // 캐시 MISS - DB에서 조회 후 캐시에 저장
            val seats = seatService.findAvailableSeatsByConcertScheduleId(command.scheduleId)
            seatCacheService.saveAvailableSeats(command.scheduleId, seats)
            seats
        }

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
