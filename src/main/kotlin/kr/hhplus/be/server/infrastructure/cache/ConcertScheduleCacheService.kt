package kr.hhplus.be.server.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSchedulesResult
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ConcertScheduleCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val SCHEDULES_CACHE_KEY_PREFIX = "concert:schedules"
        private val CACHE_TTL = Duration.ofMinutes(30) // 30분
    }

    fun getSchedules(concertId: Long): GetAvailableSchedulesResult? {
        val cached = redisTemplate.opsForValue().get(getCacheKey(concertId)) ?: return null
        return objectMapper.readValue(cached, GetAvailableSchedulesResult::class.java)
    }

    fun setSchedules(concertId: Long, result: GetAvailableSchedulesResult) {
        val json = objectMapper.writeValueAsString(result)
        redisTemplate.opsForValue().set(getCacheKey(concertId), json, CACHE_TTL)
    }

    fun evictSchedules(concertId: Long) {
        redisTemplate.delete(getCacheKey(concertId))
    }

    private fun getCacheKey(concertId: Long): String {
        return "$SCHEDULES_CACHE_KEY_PREFIX:$concertId"
    }
}
