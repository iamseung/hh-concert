package kr.hhplus.be.server.application.scheduler

import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.seat.event.SeatExpiredEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime

/**
 * 좌석 스케줄러
 *
 * 역할:
 * - 만료된 임시 예약 좌석을 주기적으로 확인
 * - Kafka로 좌석 만료 이벤트 발행
 *
 * 개선 사항 (Kafka 적용):
 * - Before: 스케줄러가 직접 좌석 복원 (동기 처리, DB 부하)
 * - After: 스케줄러는 만료 확인만 수행, Kafka로 이벤트 발행 (비동기)
 * - Consumer가 배치 단위로 좌석 복원 (부하 분산)
 */
@Component
class SeatScheduler(
    private val reservationService: ReservationService,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.seat-expired}")
    private val seatExpiredTopic: String,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 만료된 임시 좌석 확인 및 이벤트 발행
     * 매 1분마다 실행
     *
     * 개선 효과:
     * - 스케줄러는 만료 확인만 수행 (빠른 실행)
     * - 실제 복원은 Consumer가 비동기 처리
     * - 대량 좌석 만료 시에도 스케줄러 블로킹 없음
     */
    @Scheduled(fixedDelay = 60000)
    fun publishExpiredSeats() {
        try {
            val now = LocalDateTime.now()
            val expiredSeatIds = reservationService.findExpiredReservationSeatIds(now)

            if (expiredSeatIds.isEmpty()) {
                return
            }

            log.info("Found ${expiredSeatIds.size} expired seats, publishing to Kafka")

            // Kafka로 좌석 만료 이벤트 발행
            val event = SeatExpiredEvent(
                seatIds = expiredSeatIds,
                scheduleId = 0L, // TODO: scheduleId 추가 필요 (현재는 Consumer에서 조회)
                expiredAt = Instant.now(),
            )

            val future = kafkaTemplate.send(seatExpiredTopic, event)

            future.whenComplete { result, ex ->
                if (ex == null) {
                    log.info(
                        "좌석 만료 이벤트 발행 성공 - topic={}, partition={}, offset={}, seatCount={}",
                        result.recordMetadata.topic(),
                        result.recordMetadata.partition(),
                        result.recordMetadata.offset(),
                        expiredSeatIds.size,
                    )
                } else {
                    log.error(
                        "좌석 만료 이벤트 발행 실패 - seatCount={}, error={}",
                        expiredSeatIds.size,
                        ex.message,
                        ex,
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error while publishing expired seats event", e)
        }
    }
}
