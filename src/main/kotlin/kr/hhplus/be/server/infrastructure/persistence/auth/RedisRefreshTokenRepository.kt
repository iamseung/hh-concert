package kr.hhplus.be.server.infrastructure.persistence.auth

import kr.hhplus.be.server.domain.auth.repository.RefreshTokenRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisRefreshTokenRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) : RefreshTokenRepository {

    override fun save(userId: Long, refreshToken: String, expiration: Duration) {
        redisTemplate.opsForValue()
            .set(getKey(userId), refreshToken, expiration)
    }

    override fun findByUserId(userId: Long): String? {
        return redisTemplate.opsForValue().get(getKey(userId))
    }

    override fun delete(userId: Long) {
        redisTemplate.delete(getKey(userId))
    }

    private fun getKey(userId: Long): String = "refresh_token:$userId"
}
