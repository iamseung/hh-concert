package kr.hhplus.be.server.infrastructure.kafka

import kotlinx.coroutines.runBlocking
import kr.hhplus.be.server.domain.notification.event.PaymentCompletedNotificationEvent
import kr.hhplus.be.server.infrastructure.notification.NotificationColor
import kr.hhplus.be.server.infrastructure.notification.NotificationMessage
import kr.hhplus.be.server.infrastructure.notification.Notifier
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 알림 Kafka Consumer
 *
 * 역할:
 * - Kafka에서 알림 이벤트를 수신
 * - 알림 메시지 생성
 * - Notifier를 통해 알림 전송
 *
 * 책임:
 * - 이벤트 → NotificationMessage 변환
 * - Notifier에게 전송 위임
 *
 * 특징:
 * - 수동 커밋: 알림 전송 성공 후에만 오프셋 커밋
 * - 비동기 처리: Notifier는 suspend 함수
 * - 격리: 알림 실패가 메인 비즈니스에 영향 없음
 */
@Component
@ConditionalOnProperty(prefix = "notification.discord", name = ["enabled"], havingValue = "true")
class NotificationKafkaConsumer(
    private val notifier: Notifier,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 완료 알림 처리
     *
     * 실행 흐름:
     * 1. Kafka에서 메시지 수신
     * 2. NotificationMessage 생성
     * 3. Notifier로 알림 발송
     * 4. 성공 시 오프셋 커밋
     *
     * @param event 결제 완료 알림 이벤트
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = ["\${kafka.topics.notification-payment-completed}"],
        groupId = "hhplus-notification-consumer",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumePaymentCompleted(
        @Payload event: PaymentCompletedNotificationEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        logger.info(
            "결제 완료 알림 메시지 수신 - partition={}, offset={}, userId={}, reservationId={}",
            partition,
            offset,
            event.userId,
            event.reservationId,
        )

        try {
            // 알림 메시지 생성
            val message = NotificationMessage(
                title = "💳 결제 완료",
                fields = linkedMapOf(
                    "사용자 ID" to event.userId.toString(),
                    "예약 ID" to event.reservationId.toString(),
                    "콘서트" to event.concertTitle,
                    "좌석" to event.seatNumber,
                    "결제 금액" to "${event.amount}원",
                ),
                color = NotificationColor.INFO,
                timestamp = event.paidAt,
            )

            // Notifier로 알림 발송 (suspend 함수이므로 runBlocking)
            runBlocking {
                notifier.send(message)
            }

            logger.info(
                "결제 완료 알림 발송 성공 - userId={}, reservationId={}",
                event.userId,
                event.reservationId,
            )

            // 성공 시 오프셋 커밋
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "결제 완료 알림 발송 실패 - userId={}, reservationId={}, error={}",
                event.userId,
                event.reservationId,
                e.message,
                e,
            )

            // TODO: DLQ로 전송 (현재는 로깅만 하고 커밋)
            acknowledgment.acknowledge()
        }
    }
}

