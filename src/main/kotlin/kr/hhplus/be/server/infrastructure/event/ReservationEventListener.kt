package kr.hhplus.be.server.infrastructure.event

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 예약 확정 이벤트 리스너
 *
 * 역할:
 * 1. 결제 완료 후 예약 정보를 Kafka로 발행
 * 2. 비동기 처리 (@Async) - 메인 플로우(결제)에 영향 없음
 * 3. 트랜잭션 커밋 후 실행 - 데이터 일관성 보장
 *
 * Kafka 전환 이점:
 * - 이벤트 기반 아키텍처로 시스템 간 결합도 감소
 * - 메시지 영속성으로 장애 발생 시 복구 가능
 * - 여러 컨슈머가 동일 이벤트를 독립적으로 처리 가능
 * - 백프레셔 처리를 통한 트래픽 분산
 *
 * 동시성 처리:
 * - @Async 스레드풀에서 비동기 실행
 * - Kafka의 멱등성 프로듀서로 중복 발행 방지
 * - acks=all로 모든 복제본 확인 후 성공 처리
 */
@Component
class ReservationEventListener(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.reservation-confirmed}")
    private val topic: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 예약 확정 이벤트 처리
     *
     * 실행 흐름:
     * 1. 트랜잭션 커밋 후 비동기 실행
     * 2. Kafka로 메시지 발행 (reservation.confirmed 토픽)
     * 3. 성공/실패 로깅
     *
     * Kafka 메시지 특성:
     * - Key: reservationId (파티셔닝 기준)
     * - Value: ReservationConfirmedEvent (JSON 직렬화)
     * - 멱등성 프로듀서로 중복 방지
     * - 재시도 3회 (Producer Config)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReservation(event: ReservationConfirmedEvent) {
        logger.info(
            "예약 확정 이벤트 수신 - reservationId={}, concertId={}, userId={}",
            event.reservationId,
            event.concertId,
            event.userId,
        )

        try {
            // Kafka로 메시지 발행 (key: reservationId로 파티셔닝)
            val future = kafkaTemplate.send(topic, event.reservationId.toString(), event)

            future.whenComplete { result, ex ->
                if (ex == null) {
                    logger.info(
                        "Kafka 메시지 발행 성공 - topic={}, partition={}, offset={}, reservationId={}",
                        result.recordMetadata.topic(),
                        result.recordMetadata.partition(),
                        result.recordMetadata.offset(),
                        event.reservationId,
                    )
                } else {
                    logger.error(
                        "Kafka 메시지 발행 실패 - topic={}, reservationId={}, error={}",
                        topic,
                        event.reservationId,
                        ex.message,
                        ex,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Kafka 메시지 발행 예외 - topic={}, reservationId={}, error={}",
                topic,
                event.reservationId,
                e.message,
                e,
            )
        }
    }
}
