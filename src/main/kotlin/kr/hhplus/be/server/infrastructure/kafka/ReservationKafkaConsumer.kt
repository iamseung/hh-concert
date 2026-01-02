package kr.hhplus.be.server.infrastructure.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import kr.hhplus.be.server.infrastructure.client.DataPlatformClient
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 예약 확정 이벤트 Kafka Consumer
 *
 * 역할:
 * - Kafka에서 예약 확정 메시지를 수신
 * - 데이터 플랫폼으로 예약 정보 전송
 * - 수동 커밋으로 메시지 처리 보장
 * - 실패 시 Dead Letter Queue로 전송
 *
 * 특징:
 * - 멱등성: DataPlatformClient의 Idempotency Key로 중복 처리 방지
 * - 재처리: Resilience4j 재시도 후 실패 시 DLQ 전송
 * - 격리: 외부 API 장애가 다른 시스템에 영향 없음
 * - 확장성: Consumer 그룹으로 파티션별 병렬 처리
 * - DLQ: 영구 실패 메시지를 별도 토픽에 저장하여 분석/재처리
 */
@Component
class ReservationKafkaConsumer(
    private val dataPlatformClient: DataPlatformClient,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val stringRedisTemplate: StringRedisTemplate,
    @Value("\${kafka.topics.reservation-confirmed-dlq}")
    private val dlqTopic: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val IDEMPOTENCY_KEY_PREFIX = "kafka:reservation:processed:"
        private val IDEMPOTENCY_TTL: Duration = Duration.ofHours(24)
    }

    /**
     * 예약 확정 메시지 처리
     *
     * 실행 흐름:
     * 1. Kafka에서 메시지 수신
     * 2. DataPlatformClient를 통한 외부 API 호출
     * 3. 성공 시 오프셋 커밋 (수동)
     * 4. 실패 시 오프셋 미커밋 → 재처리
     *
     * 에러 처리:
     * - 일시적 오류: 재시도 (Resilience4j)
     * - 영구적 오류: 로깅 후 커밋 (Dead Letter Queue로 확장 가능)
     *
     * @param event 예약 확정 이벤트
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param acknowledgment 수동 커밋 객체
     */
    @KafkaListener(
        topics = ["\${kafka.topics.reservation-confirmed}"],
        groupId = "\${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consume(
        @Payload event: ReservationConfirmedEvent,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment,
    ) {
        logger.info(
            "Kafka 메시지 수신 - partition={}, offset={}, reservationId={}, concertId={}, userId={}",
            partition,
            offset,
            event.reservationId,
            event.concertId,
            event.userId,
        )

        // 멱등성 체크: 이미 처리된 메시지인지 확인
        val idempotencyKey = "$IDEMPOTENCY_KEY_PREFIX${event.reservationId}"
        val isNewMessage = stringRedisTemplate.opsForValue()
            .setIfAbsent(idempotencyKey, Instant.now().toString(), IDEMPOTENCY_TTL)

        if (isNewMessage == false) {
            logger.info(
                "이미 처리된 메시지 스킵 - partition={}, offset={}, reservationId={}",
                partition,
                offset,
                event.reservationId,
            )
            acknowledgment.acknowledge()
            return
        }

        try {
            // 데이터 플랫폼으로 전송 (Resilience4j 재시도 + 서킷브레이커 적용)
            dataPlatformClient.sendReservation(event)
                .doOnSuccess { response ->
                    logger.info(
                        "데이터 플랫폼 전송 성공 - partition={}, offset={}, reservationId={}, response={}",
                        partition,
                        offset,
                        event.reservationId,
                        response,
                    )
                    // 성공 시 오프셋 커밋
                    acknowledgment.acknowledge()
                }
                .doOnError { error ->
                    logger.error(
                        "데이터 플랫폼 전송 실패 - partition={}, offset={}, reservationId={}, error={}",
                        partition,
                        offset,
                        event.reservationId,
                        error.message,
                        error,
                    )
                    // DLQ로 전송
                    sendToDLQ(event, partition, offset, error)
                    acknowledgment.acknowledge()
                }
                .block() // 블로킹으로 대기
        } catch (e: Exception) {
            logger.error(
                "메시지 처리 예외 - partition={}, offset={}, reservationId={}, error={}",
                partition,
                offset,
                event.reservationId,
                e.message,
                e,
            )
            // DLQ로 전송
            sendToDLQ(event, partition, offset, e)
            acknowledgment.acknowledge()
        }
    }

    /**
     * 실패한 메시지를 Dead Letter Queue로 전송
     *
     * DLQ 메시지 헤더:
     * - X-Original-Topic: 원본 토픽명
     * - X-Original-Partition: 원본 파티션 번호
     * - X-Original-Offset: 원본 오프셋
     * - X-Exception-Message: 에러 메시지
     * - X-Exception-StackTrace: 스택 트레이스 (선택)
     * - X-Failed-Timestamp: 실패 시각 (ISO-8601)
     *
     * @param event 실패한 이벤트
     * @param partition 원본 파티션
     * @param offset 원본 오프셋
     * @param error 발생한 예외
     */
    private fun sendToDLQ(
        event: ReservationConfirmedEvent,
        partition: Int,
        offset: Long,
        error: Throwable,
    ) {
        try {
            val record = ProducerRecord<String, Any>(
                dlqTopic,
                event.reservationId.toString(), // key: reservationId
                event, // value: 원본 이벤트
            )

            // 메타데이터 헤더 추가
            record.headers().apply {
                add(RecordHeader("X-Original-Topic", "reservation.confirmed".toByteArray()))
                add(RecordHeader("X-Original-Partition", partition.toString().toByteArray()))
                add(RecordHeader("X-Original-Offset", offset.toString().toByteArray()))
                add(RecordHeader("X-Exception-Message", (error.message ?: "Unknown error").toByteArray()))
                add(RecordHeader("X-Exception-Class", error.javaClass.name.toByteArray()))
                add(RecordHeader("X-Failed-Timestamp", Instant.now().toString().toByteArray()))
            }

            kafkaTemplate.send(record).get() // 동기 전송 (DLQ 전송 실패 시 예외 발생)

            logger.warn(
                "DLQ 전송 성공 - reservationId={}, partition={}, offset={}, error={}",
                event.reservationId,
                partition,
                offset,
                error.message,
            )
        } catch (dlqError: Exception) {
            // DLQ 전송마저 실패한 경우
            logger.error(
                "DLQ 전송 실패 - reservationId={}, partition={}, offset={}, originalError={}, dlqError={}",
                event.reservationId,
                partition,
                offset,
                error.message,
                dlqError.message,
                dlqError,
            )
            // TODO: 모니터링 알림 발송 (Slack, PagerDuty 등)
            // DLQ 전송 실패는 심각한 상황이므로 즉시 알림 필요
        }
    }
}
