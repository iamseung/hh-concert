package kr.hhplus.be.server.infrastructure.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * Dead Letter Queue Consumer
 *
 * 역할:
 * - DLQ에 저장된 실패 메시지를 모니터링
 * - 실패 원인 분석 및 로깅
 * - 알림 발송 (선택)
 * - 수동 재처리 지원 (향후 확장)
 *
 * 특징:
 * - 별도 Consumer Group으로 메인 처리와 격리
 * - 메시지 헤더에서 실패 메타데이터 추출
 * - 자동 커밋 (DLQ는 재처리 불필요)
 *
 * 활용:
 * - 운영 대시보드에서 DLQ 메시지 모니터링
 * - 장애 패턴 분석
 * - 수동 개입 후 재처리
 */
@Component
class ReservationDLQConsumer {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * DLQ 메시지 처리
     *
     * 현재는 로깅과 모니터링만 수행하며,
     * 필요시 다음과 같은 기능 추가 가능:
     * - Slack/이메일 알림
     * - 모니터링 시스템 연동 (Datadog, Prometheus 등)
     * - DB에 저장하여 재처리 큐 구성
     * - 자동 재시도 (특정 조건)
     *
     * @param record DLQ 메시지 레코드 (헤더 포함)
     * @param event 실패한 예약 확정 이벤트
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = ["\${kafka.topics.reservation-confirmed-dlq}"],
        groupId = "hhplus-reservation-dlq-monitor",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeDLQ(
        record: ConsumerRecord<String, ReservationConfirmedEvent>,
        @Payload event: ReservationConfirmedEvent,
        acknowledgment: Acknowledgment,
    ) {
        // 헤더에서 메타데이터 추출
        val headers = record.headers()
        val originalTopic = headers.lastHeader("X-Original-Topic")?.value()?.decodeToString() ?: "unknown"
        val originalPartition = headers.lastHeader("X-Original-Partition")?.value()?.decodeToString() ?: "unknown"
        val originalOffset = headers.lastHeader("X-Original-Offset")?.value()?.decodeToString() ?: "unknown"
        val exceptionMessage = headers.lastHeader("X-Exception-Message")?.value()?.decodeToString() ?: "unknown"
        val exceptionClass = headers.lastHeader("X-Exception-Class")?.value()?.decodeToString() ?: "unknown"
        val failedTimestamp = headers.lastHeader("X-Failed-Timestamp")?.value()?.decodeToString() ?: "unknown"

        logger.error(
            """
            |========================================
            | DLQ 메시지 수신
            |========================================
            | Reservation ID: ${event.reservationId}
            | User ID: ${event.userId}
            | Concert ID: ${event.concertId}
            |----------------------------------------
            | Original Topic: $originalTopic
            | Original Partition: $originalPartition
            | Original Offset: $originalOffset
            |----------------------------------------
            | Exception Class: $exceptionClass
            | Exception Message: $exceptionMessage
            | Failed Timestamp: $failedTimestamp
            |========================================
            | DLQ Partition: ${record.partition()}
            | DLQ Offset: ${record.offset()}
            | DLQ Timestamp: ${record.timestamp()}
            |========================================
            """.trimMargin(),
        )

        // TODO: 알림 발송 (Slack, 이메일, PagerDuty 등)
        // TODO: 모니터링 메트릭 기록
        // TODO: DB에 저장하여 재처리 큐 구성

        // DLQ는 자동으로 커밋 (재처리는 수동으로 진행)
        acknowledgment.acknowledge()
    }
}
