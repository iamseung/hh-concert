package kr.hhplus.be.server.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.application.usecase.concert.GetConcertsResult
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ConcertCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val CONCERTS_CACHE_KEY = "concerts:all"
        private const val CONCERT_CACHE_KEY_PREFIX = "concert"
        private val CACHE_TTL = Duration.ofHours(1) // 1시간
    }

    // 콘서트 목록 캐시
    fun getConcerts(): GetConcertsResult? {
        val cached = redisTemplate.opsForValue().get(CONCERTS_CACHE_KEY) ?: return null
        return objectMapper.readValue(cached, GetConcertsResult::class.java)
    }

    fun setConcerts(result: GetConcertsResult) {
        val json = objectMapper.writeValueAsString(result)
        redisTemplate.opsForValue().set(CONCERTS_CACHE_KEY, json, CACHE_TTL)
    }

    fun evictConcerts() {
        redisTemplate.delete(CONCERTS_CACHE_KEY)
    }

    // 단일 콘서트 캐시
    fun getConcert(concertId: Long): ConcertModel? {
        val cached = redisTemplate.opsForValue().get(getConcertCacheKey(concertId)) ?: return null
        return objectMapper.readValue(cached, ConcertModel::class.java)
    }

    fun setConcert(concert: ConcertModel) {
        val json = objectMapper.writeValueAsString(concert)
        redisTemplate.opsForValue().set(getConcertCacheKey(concert.id), json, CACHE_TTL)
    }

    fun evictConcert(concertId: Long) {
        redisTemplate.delete(getConcertCacheKey(concertId))
    }

    private fun getConcertCacheKey(concertId: Long): String {
        return "$CONCERT_CACHE_KEY_PREFIX:$concertId"
    }
}
