package kr.hhplus.be.server.application.scheduler

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class SeatScheduler(
    private val seatService: SeatService,
    private val reservationService: ReservationService,
    private val seatCacheService: SeatCacheService,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 만료된 임시 좌석 복원
     * 매 1분마다 실행
     *
     * 캐시 무효화:
     * - 좌석 상태가 TEMPORARY_RESERVED → AVAILABLE로 변경되므로
     * - 영향받은 scheduleId의 캐시를 모두 무효화
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun restoreExpiredTemporarySeats() {
        try {
            val now = LocalDateTime.now()
            val expiredSeatIds = reservationService.findExpiredReservationSeatIds(now)

            if (expiredSeatIds.isEmpty()) {
                return
            }

            // 만료된 좌석 정보 조회 (캐시 무효화를 위해 scheduleId 필요)
            val expiredSeats = seatService.findAllById(expiredSeatIds)

            // 좌석 복원
            val restoredCount = seatService.restoreExpiredSeats(expiredSeatIds)

            // 영향받은 scheduleId 목록 추출 및 캐시 무효화
            val affectedScheduleIds = expiredSeats.map { it.concertScheduleId }.distinct()
            affectedScheduleIds.forEach { scheduleId ->
                seatCacheService.evictAvailableSeats(scheduleId)
            }

            if (restoredCount > 0) {
                log.info(
                    "Restored $restoredCount expired temporary seats to AVAILABLE, " +
                        "invalidated cache for ${affectedScheduleIds.size} schedules",
                )
            }
        } catch (e: Exception) {
            log.error("Error while restoring expired temporary seats", e)
        }
    }
}
