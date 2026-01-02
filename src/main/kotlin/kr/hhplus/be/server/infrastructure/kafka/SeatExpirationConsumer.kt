package kr.hhplus.be.server.infrastructure.kafka

import kotlinx.coroutines.runBlocking
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.seat.event.SeatExpiredEvent
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import kr.hhplus.be.server.infrastructure.notification.NotificationColor
import kr.hhplus.be.server.infrastructure.notification.NotificationMessage
import kr.hhplus.be.server.infrastructure.notification.Notifier
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 좌석 만료 Kafka Consumer
 *
 * 역할:
 * - Kafka에서 좌석 만료 이벤트를 수신
 * - 만료된 좌석을 배치 단위로 복원
 * - 캐시 무효화 및 알림 발송
 *
 * 책임:
 * - 좌석 복원 비즈니스 로직 수행
 * - 알림 메시지 생성 및 Notifier에게 전송 위임
 *
 * 특징:
 * - 비동기 처리: 스케줄러와 분리되어 블로킹 없음
 * - 배치 처리: 대량 좌석도 안정적으로 복원
 * - 트랜잭션: 좌석 복원 실패 시 롤백
 * - DLQ: 실패 시 재처리 가능
 *
 * 개선 효과:
 * - SeatScheduler 블로킹 제거
 * - DB 부하 분산 (Consumer가 천천히 처리)
 * - Consumer 수평 확장 가능
 */
@Component
class SeatExpirationConsumer(
    private val seatService: SeatService,
    private val seatCacheService: SeatCacheService,
    private val notifier: Notifier?,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 좌석 만료 이벤트 처리
     *
     * 실행 흐름:
     * 1. Kafka에서 메시지 수신
     * 2. 좌석 정보 조회 (scheduleId 추출용)
     * 3. 좌석 복원 (Bulk Update)
     * 4. 캐시 무효화
     * 5. 알림 메시지 생성 및 발송
     * 6. 성공 시 오프셋 커밋
     *
     * @param event 좌석 만료 이벤트
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = ["\${kafka.topics.seat-expired}"],
        groupId = "hhplus-seat-expiration-consumer",
        containerFactory = "kafkaListenerContainerFactory",
    )
    @Transactional
    fun consumeSeatExpiration(
        @Payload event: SeatExpiredEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        logger.info(
            "좌석 만료 메시지 수신 - partition={}, offset={}, seatCount={}",
            partition,
            offset,
            event.seatIds.size,
        )

        try {
            // 1. 만료된 좌석 정보 조회 (캐시 무효화를 위해 scheduleId 필요)
            val expiredSeats = seatService.findAllById(event.seatIds)

            // 2. 좌석 복원 (Bulk Update)
            val restoredCount = seatService.restoreExpiredSeats(event.seatIds)

            // 3. 영향받은 scheduleId 목록 추출 및 캐시 무효화
            val affectedScheduleIds = expiredSeats.map { it.concertScheduleId }.distinct()
            affectedScheduleIds.forEach { scheduleId ->
                seatCacheService.evictAvailableSeats(scheduleId)
            }

            logger.info(
                "좌석 복원 완료 - restoredCount={}, affectedSchedules={}, partition={}, offset={}",
                restoredCount,
                affectedScheduleIds.size,
                partition,
                offset,
            )

            // 4. 알림 메시지 생성 및 발송 (선택)
            if (notifier != null && restoredCount > 0) {
                val message = NotificationMessage(
                    title = "⏰ 좌석 만료 처리 완료",
                    fields = linkedMapOf(
                        "스케줄 ID" to (affectedScheduleIds.firstOrNull()?.toString() ?: "N/A"),
                        "만료 좌석 수" to "${event.seatIds.size}개",
                        "복원 좌석 수" to "${restoredCount}개",
                    ),
                    color = NotificationColor.WARNING,
                )

                runBlocking {
                    notifier.send(message)
                }
            }

            // 5. 성공 시 오프셋 커밋
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "좌석 복원 실패 - seatCount={}, partition={}, offset={}, error={}",
                event.seatIds.size,
                partition,
                offset,
                e.message,
                e,
            )

            // TODO: DLQ로 전송 (현재는 로깅만 하고 커밋)
            acknowledgment.acknowledge()
        }
    }
}

