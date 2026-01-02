package kr.hhplus.be.server.domain.notification.event

import java.time.LocalDateTime

/**
 * 결제 완료 알림 이벤트
 *
 * 발행 시점: 결제 완료 직후
 * 발행 위치: PaymentEventListener (가정) 또는 PaymentUseCase
 * 소비자: NotificationConsumer
 *
 * 용도:
 * - 사용자에게 "결제가 완료되었습니다" Push 알림 발송
 * - 예약 확정 알림
 */
data class PaymentCompletedNotificationEvent(
    val userId: Long,
    val reservationId: Long,
    val concertId: Long,
    val concertTitle: String,
    val seatNumber: String,
    val amount: Long,
    val paidAt: LocalDateTime,
)
