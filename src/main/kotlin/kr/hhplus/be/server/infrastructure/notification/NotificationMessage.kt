package kr.hhplus.be.server.infrastructure.notification

import java.time.LocalDateTime

/**
 * 알림 메시지 DTO
 *
 * 다양한 알림 채널(Discord, Kakao, Firebase 등)에서
 * 공통적으로 사용할 수 있는 범용 메시지 구조입니다.
 *
 * 각 구현체는 이 구조를 자신의 포맷으로 변환합니다:
 * - DiscordNotifier: Discord Embed 형식
 * - KakaoNotifier: 카카오톡 템플릿 형식
 * - FirebaseNotifier: FCM 메시지 형식
 */
data class NotificationMessage(
    /**
     * 메시지 제목
     */
    val title: String,

    /**
     * 메시지 본문 (선택)
     */
    val content: String? = null,

    /**
     * 추가 필드 (key-value)
     * 예: mapOf("사용자 ID" to "123", "예약 ID" to "456")
     */
    val fields: Map<String, String> = emptyMap(),

    /**
     * 메시지 색상 (선택)
     * Discord: RGB 정수값 (예: 0x00FF00)
     * Kakao: 무시
     */
    val color: NotificationColor = NotificationColor.DEFAULT,

    /**
     * 타임스탬프 (선택)
     */
    val timestamp: LocalDateTime = LocalDateTime.now(),
)

/**
 * 알림 색상 열거형
 */
enum class NotificationColor(val rgbValue: Int) {
    SUCCESS(0x00FF00),  // 녹색
    ERROR(0xFF0000),    // 빨강
    WARNING(0xFFA500),  // 주황
    INFO(0x0000FF),     // 파란색
    DEFAULT(0x808080),  // 회색
}
