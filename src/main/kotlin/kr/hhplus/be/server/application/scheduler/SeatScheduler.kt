package kr.hhplus.be.server.application.scheduler

import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class SeatScheduler(
    private val seatService: SeatService,
    private val reservationRepository: ReservationRepository,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 만료된 임시 좌석 복원
     * 매 1분마다 실행
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun restoreExpiredTemporarySeats() {
        try {
            val now = LocalDateTime.now()
            val expiredSeatIds = reservationRepository.findExpiredReservationSeatIds(now)
            val restoredCount = seatService.restoreExpiredSeats(expiredSeatIds)

            if (restoredCount > 0) {
                log.info("Restored $restoredCount expired temporary seats to AVAILABLE")
            }
        } catch (e: Exception) {
            log.error("Error while restoring expired temporary seats", e)
        }
    }
}
