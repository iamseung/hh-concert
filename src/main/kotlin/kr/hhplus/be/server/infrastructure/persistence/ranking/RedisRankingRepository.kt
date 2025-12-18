package kr.hhplus.be.server.infrastructure.persistence.ranking

import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import kr.hhplus.be.server.domain.ranking.model.RankingModel
import kr.hhplus.be.server.domain.ranking.repository.RankingRepository
import kr.hhplus.be.server.infrastructure.persistence.redis.RedisRepository
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Redis 기반 랭킹 저장소 구현체
 *
 * Redis 자료구조:
 * - Sorted Set: concert:ranking (concertId → recentSales)
 * - List: concert:{concertId}:sales (판매 이벤트 타임스탬프)
 * - Hash: concert:{concertId}:info (콘서트 메타정보)
 */
@Repository
class RedisRankingRepository(
    private val redisRepository: RedisRepository,
    private val stringRedisTemplate: StringRedisTemplate,
    private val concertRepository: ConcertRepository,
) : RankingRepository {

    companion object {
        private const val RANKING_KEY = "concert:ranking"
        private const val SALES_KEY_PREFIX = "concert:"
        private const val SALES_KEY_SUFFIX = ":sales"
        private const val INFO_KEY_PREFIX = "concert:"
        private const val INFO_KEY_SUFFIX = ":info"
        private const val INFO_FIELD_TITLE = "title"

        /**
         * List의 최대 크기 (메모리 효율성)
         * 최근 1000개 판매 이벤트만 유지
         */
        private const val MAX_SALES_LIST_SIZE = 1000L
    }

    override fun recordSale(concertId: Long, timestamp: Long) {
        val key = getSalesKey(concertId)

        // List의 앞쪽에 추가 (LPUSH)
        stringRedisTemplate.execute(
            RedisCallback<Unit> { connection ->
                connection.listCommands().lPush(
                    key.toByteArray(),
                    timestamp.toString().toByteArray(),
                )

                // List 크기 제한 (LTRIM으로 최근 N개만 유지)
                connection.listCommands().lTrim(
                    key.toByteArray(),
                    0,
                    MAX_SALES_LIST_SIZE - 1,
                )
                null
            },
        )
    }

    override fun incrementRankingScore(concertId: Long, increment: Double) {
        stringRedisTemplate.opsForZSet().incrementScore(
            RANKING_KEY,
            concertId.toString(),
            increment,
        )
    }

    override fun getTopRankings(limit: Int): List<RankingModel> {
        // Sorted Set에서 상위 N개 조회 (내림차순)
        val rankedConcerts = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(RANKING_KEY, 0, (limit - 1).toLong())
            ?: emptySet()

        return rankedConcerts.mapIndexed { index, typedTuple ->
            val concertId = typedTuple.value?.toLongOrNull() ?: 0L
            val score = typedTuple.score ?: 0.0
            val title = getConcertTitle(concertId) ?: "Unknown"

            RankingModel.from(
                rank = index.toLong(),
                concertId = concertId,
                concertTitle = title,
                score = score,
            )
        }
    }

    override fun removeOldSales(concertId: Long, cutoffTimestamp: Long): Long {
        val key = getSalesKey(concertId)

        // List의 모든 요소를 읽어서 cutoff 이후의 것만 필터링
        val allSales = stringRedisTemplate.opsForList().range(key, 0, -1) ?: emptyList()

        val validSales = allSales.filter { it.toLong() >= cutoffTimestamp }

        // 기존 List 삭제 후 유효한 데이터만 재저장
        if (allSales.isNotEmpty()) {
            redisRepository.delete(key)

            if (validSales.isNotEmpty()) {
                stringRedisTemplate.opsForList().rightPushAll(key, validSales)
            }
        }

        return (allSales.size - validSales.size).toLong()
    }

    override fun calculateRecentSales(concertId: Long, windowMinutes: Int): Long {
        val key = getSalesKey(concertId)
        val now = LocalDateTime.now()
        val cutoffTime = now.minusMinutes(windowMinutes.toLong())
        val cutoffTimestamp = cutoffTime.toInstant(ZoneOffset.UTC).toEpochMilli()

        val allSales = stringRedisTemplate.opsForList().range(key, 0, -1) ?: emptyList()

        return allSales.count { it.toLong() >= cutoffTimestamp }.toLong()
    }

    override fun updateRankingScore(concertId: Long, score: Double) {
        redisRepository.zAdd(RANKING_KEY, concertId.toString(), score)
    }

    override fun getAllConcertIds(): List<Long> {
        // Sorted Set의 모든 concertId 조회
        val allMembers = redisRepository.zRange(RANKING_KEY, 0, -1)
        return allMembers.mapNotNull { it.toLongOrNull() }
    }

    override fun saveConcertMetadata(concertId: Long, title: String) {
        val key = getInfoKey(concertId)
        redisRepository.hSet(key, INFO_FIELD_TITLE, title)
    }

    override fun getConcertTitle(concertId: Long): String? {
        val key = getInfoKey(concertId)
        return redisRepository.hGet(key, INFO_FIELD_TITLE)
            ?: fetchAndCacheConcertTitle(concertId)
    }

    /**
     * DB에서 콘서트 제목을 조회하여 캐싱합니다.
     */
    private fun fetchAndCacheConcertTitle(concertId: Long): String? {
        return try {
            val concert = concertRepository.findById(concertId)
            concert?.let {
                saveConcertMetadata(concertId, it.title)
                it.title
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSalesKey(concertId: Long): String {
        return "$SALES_KEY_PREFIX$concertId$SALES_KEY_SUFFIX"
    }

    private fun getInfoKey(concertId: Long): String {
        return "$INFO_KEY_PREFIX$concertId$INFO_KEY_SUFFIX"
    }
}
