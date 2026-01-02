package kr.hhplus.be.server.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.time.format.DateTimeFormatter

/**
 * Discord Webhook 알림 구현체
 *
 * 역할:
 * - NotificationMessage를 Discord Embed 형식으로 변환
 * - Discord Webhook으로 전송
 *
 * 책임:
 * - 메시지 전송만 담당
 * - 메시지 포맷팅은 호출자가 담당
 *
 * 설정:
 * - notification.discord.enabled: 알림 활성화 여부
 * - notification.discord.webhook-url: Discord Webhook URL
 *
 * 참고:
 * - 실제 프로덕션에서는 Firebase Cloud Messaging, AWS SNS 등 사용
 * - 현재는 개발/테스트 용도로 Discord 사용
 */
@Service
@ConditionalOnProperty(prefix = "notification.discord", name = ["enabled"], havingValue = "true")
class DiscordNotifier(
    private val webClient: WebClient,
    @Value("\${notification.discord.webhook-url:}") private val webhookUrl: String,
) : Notifier {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 알림 메시지 전송
     *
     * NotificationMessage를 Discord Embed 형식으로 변환하여 전송합니다.
     *
     * @param message 전송할 알림 메시지
     */
    override suspend fun send(message: NotificationMessage) {
        if (webhookUrl.isBlank()) {
            logger.warn("Discord webhook URL is not configured, skipping notification")
            return
        }

        val embed = convertToDiscordEmbed(message)
        sendToDiscord(embed)
    }

    /**
     * NotificationMessage를 Discord Embed 형식으로 변환
     */
    private fun convertToDiscordEmbed(message: NotificationMessage): Map<String, Any> {
        val fields = message.fields.map { (name, value) ->
            mapOf(
                "name" to name,
                "value" to value,
                "inline" to true,
            )
        }

        val embedContent = mutableMapOf(
            "title" to message.title,
            "color" to message.color.rgbValue,
            "fields" to fields,
            "timestamp" to message.timestamp.format(DateTimeFormatter.ISO_DATE_TIME),
        )

        // content가 있으면 description으로 추가
        if (!message.content.isNullOrBlank()) {
            embedContent["description"] = message.content
        }

        return mapOf("embeds" to listOf(embedContent))
    }

    /**
     * Discord Webhook으로 전송
     */
    private suspend fun sendToDiscord(payload: Map<String, Any>) {
        try {
            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .awaitBodyOrNull<String>()

            logger.debug("Discord notification sent successfully")
        } catch (e: Exception) {
            logger.error("Failed to send Discord notification: ${e.message}", e)
        }
    }
}

