package kr.hhplus.be.server.domain.notification.event

import java.time.LocalDateTime

/**
 * 예약 완료 알림 이벤트
 *
 * 발행 시점: 좌석 예약 완료 직후
 * 발행 위치: ReservationEventListener (Kafka로 전달)
 * 소비자: NotificationConsumer
 *
 * 용도:
 * - 사용자에게 "좌석 예약이 완료되었습니다" Push 알림 발송
 * - Discord/Firebase/SMS 등 다양한 알림 채널로 전송
 */
data class ReservationCompletedNotificationEvent(
    val userId: Long,
    val reservationId: Long,
    val concertId: Long,
    val concertTitle: String,
    val seatNumber: String,
    val reservedAt: LocalDateTime,
    val expiresAt: LocalDateTime, // 임시 예약 만료 시간 (5분)
)
