package kr.hhplus.be.server.infrastructure.event

import kr.hhplus.be.server.domain.notification.event.PaymentCompletedNotificationEvent
import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

/**
 * 알림 이벤트 리스너
 *
 * 역할:
 * - 예약/결제 완료 시 사용자에게 알림을 보내기 위한 이벤트 발행
 * - Spring ApplicationEvent → Kafka 변환
 * - 알림 전송 실패가 메인 비즈니스 로직에 영향 없도록 격리
 *
 * 처리 흐름:
 * 1. 트랜잭션 커밋 후 비동기 실행 (@Async)
 * 2. Kafka로 알림 이벤트 발행
 * 3. NotificationConsumer가 Discord/Firebase로 알림 발송
 *
 * 특징:
 * - 비동기 처리로 메인 로직과 격리
 * - Kafka로 메시지 영속화 (알림 실패 시 재시도 가능)
 * - 다양한 알림 채널(Push/SMS/Email) 독립적 확장
 */
@Component
class NotificationEventListener(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.notification-payment-completed}")
    private val paymentCompletedTopic: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 결제 완료 알림 이벤트 발행
     *
     * 실행 시점: 결제 완료 후 (예약 확정)
     * 발행 토픽: notifications.payment-completed
     *
     * @param event 예약 확정 이벤트 (결제 완료)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCompleted(event: ReservationConfirmedEvent) {
        logger.info(
            "결제 완료 알림 이벤트 발행 - reservationId={}, userId={}",
            event.reservationId,
            event.userId,
        )

        try {
            // TODO: 좌석 정보, 금액 등 추가 정보 조회 필요
            // 현재는 간단히 이벤트 정보만 사용
            val notificationEvent = PaymentCompletedNotificationEvent(
                userId = event.userId,
                reservationId = event.reservationId,
                concertId = event.concertId,
                concertTitle = event.concertTitle,
                seatNumber = "정보 없음", // TODO: 좌석 정보 조회
                amount = 0L, // TODO: 결제 금액 조회
                paidAt = LocalDateTime.now(),
            )

            val future = kafkaTemplate.send(
                paymentCompletedTopic,
                event.userId.toString(), // Key: userId (파티셔닝)
                notificationEvent,
            )

            future.whenComplete { result, ex ->
                if (ex == null) {
                    logger.info(
                        "결제 완료 알림 이벤트 발행 성공 - topic={}, partition={}, offset={}, userId={}",
                        result.recordMetadata.topic(),
                        result.recordMetadata.partition(),
                        result.recordMetadata.offset(),
                        event.userId,
                    )
                } else {
                    logger.error(
                        "결제 완료 알림 이벤트 발행 실패 - userId={}, error={}",
                        event.userId,
                        ex.message,
                        ex,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                "결제 완료 알림 이벤트 발행 예외 - userId={}, error={}",
                event.userId,
                e.message,
                e,
            )
        }
    }
}
