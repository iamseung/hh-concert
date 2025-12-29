package kr.hhplus.be.server.infrastructure.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.concert.model.SeatModel
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 좌석 조회 캐시 서비스
 *
 * 캐시 전략:
 * - Cache Aside 패턴: 조회 시 캐시 미스 → DB 조회 → 캐시 저장
 * - TTL: 10초 (좌석 상태의 실시간성 보장)
 * - 무효화: 좌석 예약/취소 시 즉시 삭제
 *
 * 성능 개선 목표:
 * - 대량 트래픽 시 DB 부하 감소
 * - 응답 시간 단축 (DB → Redis)
 */
@Service
class SeatCacheService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CACHE_KEY_PREFIX = "concert:seats"
        private const val TTL_SECONDS = 10L
    }

    /**
     * 좌석 목록 캐시 조회
     *
     * @param scheduleId 콘서트 일정 ID
     * @return 캐시된 좌석 목록, 없으면 null
     */
    fun getAvailableSeats(scheduleId: Long): List<SeatModel>? {
        return try {
            val key = getCacheKey(scheduleId)
            val cached = redisTemplate.opsForValue().get(key)

            if (cached != null) {
                log.debug("좌석 캐시 HIT: scheduleId={}", scheduleId)
                // JSON으로 저장된 데이터를 List<SeatModel>로 변환
                val jsonString = objectMapper.writeValueAsString(cached)
                objectMapper.readValue(jsonString, object : TypeReference<List<SeatModel>>() {})
            } else {
                log.debug("좌석 캐시 MISS: scheduleId={}", scheduleId)
                null
            }
        } catch (e: Exception) {
            log.error("좌석 캐시 조회 실패: scheduleId={}", scheduleId, e)
            null // 캐시 오류 시 null 반환하여 DB 조회로 폴백
        }
    }

    /**
     * 좌석 목록 캐시 저장
     *
     * @param scheduleId 콘서트 일정 ID
     * @param seats 좌석 목록
     */
    fun saveAvailableSeats(scheduleId: Long, seats: List<SeatModel>) {
        try {
            val key = getCacheKey(scheduleId)
            redisTemplate.opsForValue().set(key, seats, TTL_SECONDS, TimeUnit.SECONDS)
            log.debug("좌석 캐시 저장: scheduleId={}, count={}, ttl={}s", scheduleId, seats.size, TTL_SECONDS)
        } catch (e: Exception) {
            log.error("좌석 캐시 저장 실패: scheduleId={}", scheduleId, e)
            // 캐시 저장 실패는 무시 (애플리케이션 동작에 영향 없음)
        }
    }

    /**
     * 좌석 캐시 무효화
     *
     * 호출 시점:
     * - 좌석 예약 시 (AVAILABLE → TEMPORARY_RESERVED)
     * - 좌석 예약 취소 시 (TEMPORARY_RESERVED → AVAILABLE)
     * - 좌석 예약 만료 시 (TEMPORARY_RESERVED → AVAILABLE)
     * - 좌석 결제 완료 시 (TEMPORARY_RESERVED → RESERVED)
     *
     * @param scheduleId 콘서트 일정 ID
     */
    fun evictAvailableSeats(scheduleId: Long) {
        try {
            val key = getCacheKey(scheduleId)
            val deleted = redisTemplate.delete(key)
            if (deleted) {
                log.debug("좌석 캐시 무효화: scheduleId={}", scheduleId)
            } else {
                log.debug("좌석 캐시 없음 (무효화 불필요): scheduleId={}", scheduleId)
            }
        } catch (e: Exception) {
            log.error("좌석 캐시 무효화 실패: scheduleId={}", scheduleId, e)
            // 캐시 무효화 실패는 무시 (TTL로 자동 만료됨)
        }
    }

    /**
     * 캐시 키 생성
     */
    private fun getCacheKey(scheduleId: Long): String {
        return "$CACHE_KEY_PREFIX:$scheduleId"
    }
}
