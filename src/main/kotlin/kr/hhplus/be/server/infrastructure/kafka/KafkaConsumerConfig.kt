package kr.hhplus.be.server.infrastructure.kafka

import kr.hhplus.be.server.domain.reservation.event.ReservationConfirmedEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer

/**
 * Kafka Consumer 설정
 *
 * 특징:
 * - 수동 커밋 (enable.auto.commit=false)
 * - JSON 역직렬화 (Jackson)
 * - ErrorHandlingDeserializer로 역직렬화 에러 처리
 * - 트랜잭션 격리 수준: read_committed
 */
@Configuration
class KafkaConsumerConfig {

    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${kafka.consumer.group-id}")
    private lateinit var groupId: String

    @Bean
    fun consumerFactory(): ConsumerFactory<String, ReservationConfirmedEvent> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS to StringDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to "kr.hhplus.be.server.domain.reservation.event",
            JsonDeserializer.VALUE_DEFAULT_TYPE to ReservationConfirmedEvent::class.java.name,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ReservationConfirmedEvent> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ReservationConfirmedEvent>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }
}
