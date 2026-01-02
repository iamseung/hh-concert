package kr.hhplus.be.server.infrastructure.notification

/**
 * 알림 발송 인터페이스
 *
 * 다양한 알림 채널(Discord, Kakao, SMS, Firebase 등)을
 * 동일한 방식으로 사용할 수 있도록 추상화합니다.
 *
 * 책임:
 * - 알림 메시지 전송만 담당
 * - 메시지 포맷팅은 호출자(Consumer, Service)가 담당
 *
 * 구현체:
 * - DiscordNotifier: Discord Webhook 전송
 * - KakaoNotifier: 카카오톡 알림 (추후 구현)
 * - FirebaseNotifier: Firebase Cloud Messaging (추후 구현)
 * - SmsNotifier: AWS SNS SMS (추후 구현)
 */
interface Notifier {

    /**
     * 알림 메시지 전송
     *
     * @param message 전송할 알림 메시지
     */
    suspend fun send(message: NotificationMessage)
}
