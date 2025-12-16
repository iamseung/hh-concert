package kr.hhplus.be.server.infrastructure.configuration

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig(
    @Value("\${spring.data.redis.host}")
    private val host: String,

    @Value("\${spring.data.redis.port}")
    private val port: Int,
) {

    /**
     * 복잡한 객체를 Redis에 저장할 때 사용하는 템플릿
     * - Value를 JSON 형태로 직렬화/역직렬화 (GenericJackson2JsonRedisSerializer)
     * - 도메인 객체, DTO 등을 캐싱할 때 사용
     * - 예: redisTemplate.opsForValue().set("user:1", userObject)
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            this.connectionFactory = connectionFactory

            // Key Serializer
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()

            // Value Serializer
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()
        }
    }

    /**
     * 단순 문자열 데이터를 Redis에 저장할 때 사용하는 템플릿
     * - Key와 Value 모두 String으로 직렬화 (StringRedisSerializer)
     * - 카운터, 간단한 캐시, 토큰 저장 등에 사용
     * - redisTemplate보다 가볍고 빠름
     * - 예: stringRedisTemplate.opsForValue().set("counter", "100")
     */
    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    /**
     * Redisson 클라이언트 - 분산 환경에서의 고급 기능 제공
     * - 분산 락 (Distributed Lock): 동시성 제어
     * - 분산 컬렉션: RMap, RList, RSet 등
     * - Pub/Sub, 분산 세마포어, 카운트다운 래치 등
     * - 예: redissonClient.getLock("reservation:lock").lock()
     */
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer().setAddress("redis://$host:$port")

        return Redisson.create(config)
    }
}
