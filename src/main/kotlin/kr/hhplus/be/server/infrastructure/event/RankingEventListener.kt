package kr.hhplus.be.server.infrastructure.event

import kr.hhplus.be.server.domain.ranking.service.RankingService
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 랭킹 이벤트 리스너
 *
 * 예약 확정 이벤트를 수신하여 랭킹을 실시간으로 업데이트합니다.
 */
@Component
class RankingEventListener(
    private val rankingService: RankingService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 확정 시 랭킹 업데이트
     *
     * 트랜잭션 커밋 후에 실행되어 데이터 일관성을 보장합니다.
     * 비동기로 실행되어 결제 응답 시간에 영향을 주지 않습니다.
     *
     * @param event 예약 확정 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservationConfirmed(event: ReservationConfirmedEvent) {
        try {
            logger.info(
                "Updating ranking for concert {} (reservationId: {})",
                event.concertId,
                event.reservationId,
            )

            rankingService.onReservationConfirmed(
                concertId = event.concertId,
                concertTitle = event.concertTitle,
            )

            logger.info("Ranking updated successfully for concert {}", event.concertId)
        } catch (e: Exception) {
            // 랭킹 업데이트 실패는 결제 성공에 영향을 주지 않습니다
            logger.error("Failed to update ranking for concert {}: {}", event.concertId, e.message, e)
        }
    }
}
