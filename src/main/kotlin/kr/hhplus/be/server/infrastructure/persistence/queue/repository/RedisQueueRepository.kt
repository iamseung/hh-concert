package kr.hhplus.be.server.infrastructure.persistence.queue.repository

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.repository.QueueTokenRepository
import kr.hhplus.be.server.infrastructure.persistence.queue.entity.QueueTokenRedisEntity
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Repository
class RedisQueueRepository(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val stringRedisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : QueueTokenRepository {

    // Lua 스크립트 로드
    private val activateUsersScript: RedisScript<String> = RedisScript.of(
        ClassPathResource("scripts/activate_waiting_users.lua"),
        String::class.java,
    )

    private val expireTokensScript: RedisScript<String> = RedisScript.of(
        ClassPathResource("scripts/remove_expired_active_tokens.lua"),
        String::class.java,
    )

    companion object {
        private const val WAITING_QUEUE_KEY = "queue:waiting"
        private const val ACTIVE_QUEUE_KEY = "queue:active"
        private const val TOKEN_KEY_PREFIX = "queue:token:"
        private const val TOKEN_TO_USERID_KEY = "queue:token_to_userid:"
    }

    /**
     * 대기열에 사용자 추가
     * Score: 진입 시간 (timestamp)
     */
    fun addToWaitingQueue(userId: Long): Long {
        val score = System.currentTimeMillis().toDouble()
        stringRedisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, userId.toString(), score)
        return getPosition(userId) ?: 0
    }

    /**
     * 대기열에서 사용자의 순위 조회 (0부터 시작)
     */
    fun getPosition(userId: Long): Long? {
        val rank = stringRedisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId.toString())
        return rank?.plus(1) // 1부터 시작하도록 변환
    }

    /**
     * WAITING 대기열 크기 조회
     */
    fun getWaitingQueueSize(): Long {
        return stringRedisTemplate.opsForZSet().size(WAITING_QUEUE_KEY) ?: 0
    }

    /**
     * ACTIVE 대기열 크기 조회
     */
    fun getActiveQueueSize(): Long {
        return stringRedisTemplate.opsForZSet().size(ACTIVE_QUEUE_KEY) ?: 0
    }

    /**
     * 대기열에서 상위 N명을 ACTIVE로 이동 (Lua 스크립트로 원자화)
     */
    fun activateWaitingUsers(count: Int): List<Long> {
        val now = LocalDateTime.now()
        val expiryTime = now.plusMinutes(10)
        val expiryScore = expiryTime.toInstant(ZoneOffset.UTC).toEpochMilli()

        // Lua 스크립트 실행
        val resultJson = stringRedisTemplate.execute(
            activateUsersScript,
            listOf(WAITING_QUEUE_KEY, ACTIVE_QUEUE_KEY),
            count.toString(),
            expiryScore.toString(),
            now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            expiryTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        ) ?: "[]"

        // JSON 결과를 List<Long>으로 파싱
        return try {
            objectMapper.readValue(resultJson, Array<String>::class.java)
                .map { it.toLong() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 만료된 ACTIVE 토큰 제거 (Lua 스크립트로 원자화)
     */
    fun removeExpiredActiveTokens(): List<Long> {
        val now = System.currentTimeMillis()

        // Lua 스크립트 실행
        val resultJson = stringRedisTemplate.execute(
            expireTokensScript,
            listOf(ACTIVE_QUEUE_KEY),
            now.toString(),
        ) ?: "[]"

        // JSON 결과를 List<Long>으로 파싱
        return try {
            objectMapper.readValue(resultJson, Array<String>::class.java)
                .map { it.toLong() }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 사용자가 WAITING에 있는지 확인
     */
    fun isInWaitingQueue(userId: Long): Boolean {
        val score = stringRedisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, userId.toString())
        return score != null
    }

    /**
     * 사용자가 ACTIVE에 있는지 확인
     */
    fun isInActiveQueue(userId: Long): Boolean {
        val score = stringRedisTemplate.opsForZSet().score(ACTIVE_QUEUE_KEY, userId.toString())
        return score != null
    }

    /**
     * ACTIVE에서 사용자 제거 (토큰 만료 처리)
     */
    fun removeFromActiveQueue(userId: Long) {
        stringRedisTemplate.opsForZSet().remove(ACTIVE_QUEUE_KEY, userId.toString())
        redisTemplate.delete("$TOKEN_KEY_PREFIX$userId")
    }

    /**
     * Token Entity 저장
     */
    fun saveTokenEntity(entity: QueueTokenRedisEntity) {
        val tokenKey = "$TOKEN_KEY_PREFIX${entity.userId}"
        stringRedisTemplate.opsForHash<String, String>().putAll(tokenKey, entity.toHash().mapValues { it.value.toString() })
    }

    /**
     * Token Entity 조회
     */
    fun getTokenEntity(userId: Long): QueueTokenRedisEntity? {
        val tokenKey = "$TOKEN_KEY_PREFIX$userId"
        val hash = stringRedisTemplate.opsForHash<String, String>().entries(tokenKey)
        return if (hash.isEmpty()) null else QueueTokenRedisEntity.fromHash(hash)
    }

    /**
     * WAITING Queue의 모든 사용자 조회
     */
    fun getAllWaitingUsers(): List<Long> {
        return stringRedisTemplate.opsForZSet()
            .range(WAITING_QUEUE_KEY, 0, -1)
            ?.map { it.toLong() }
            ?: emptyList()
    }

    /**
     * ACTIVE Queue의 모든 사용자 조회
     */
    fun getAllActiveUsers(): List<Long> {
        return stringRedisTemplate.opsForZSet()
            .range(ACTIVE_QUEUE_KEY, 0, -1)
            ?.map { it.toLong() }
            ?: emptyList()
    }

    // ===== QueueTokenRepository 인터페이스 구현 =====

    override fun save(queueTokenModel: QueueTokenModel): QueueTokenModel {
        // 1. Token → UserId 매핑 저장
        redisTemplate.opsForValue().set(
            "$TOKEN_TO_USERID_KEY${queueTokenModel.token}",
            queueTokenModel.userId,
        )

        // 2. Redis Entity 생성 및 저장
        val entity = QueueTokenRedisEntity.fromDomain(queueTokenModel)
        saveTokenEntity(entity)

        // 3. 상태에 따라 적절한 Queue에 추가
        when (queueTokenModel.queueStatus) {
            QueueStatus.WAITING -> {
                addToWaitingQueue(queueTokenModel.userId)
            }
            QueueStatus.ACTIVE -> {
                // ACTIVE는 activateWaitingUsers에서 처리되므로 여기서는 skip
            }
            QueueStatus.EXPIRED -> {
                // EXPIRED는 제거
                removeFromActiveQueue(queueTokenModel.userId)
            }
        }

        return queueTokenModel
    }

    override fun update(queueTokenModel: QueueTokenModel): QueueTokenModel {
        // Redis Entity 생성 및 업데이트
        val entity = QueueTokenRedisEntity.fromDomain(queueTokenModel)
        saveTokenEntity(entity)

        // 상태 변경에 따른 Queue 이동 처리
        when (queueTokenModel.queueStatus) {
            QueueStatus.EXPIRED -> {
                removeFromActiveQueue(queueTokenModel.userId)
            }
            else -> {
                // 다른 상태 변경은 별도 메서드로 처리
            }
        }

        return queueTokenModel
    }

    override fun findByUserId(userId: Long): QueueTokenModel? {
        return getTokenEntity(userId)?.toModel()
    }

    override fun findByToken(token: String): QueueTokenModel? {
        // token → userId 조회
        val userId = redisTemplate.opsForValue()
            .get("$TOKEN_TO_USERID_KEY$token")?.toString()?.toLongOrNull()
            ?: return null

        return getTokenEntity(userId)?.toModel()
    }

    override fun findByTokenOrThrow(token: String): QueueTokenModel {
        return findByToken(token) ?: throw BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND)
    }

    override fun findAllByStatus(status: QueueStatus): List<QueueTokenModel> {
        return when (status) {
            QueueStatus.WAITING -> {
                getAllWaitingUsers().mapNotNull { userId ->
                    getTokenEntity(userId)?.toModel()
                }
            }
            QueueStatus.ACTIVE -> {
                getAllActiveUsers().mapNotNull { userId ->
                    getTokenEntity(userId)?.toModel()
                }
            }
            QueueStatus.EXPIRED -> {
                emptyList()
            }
        }
    }

    override fun countByStatus(status: QueueStatus): Long {
        return when (status) {
            QueueStatus.WAITING -> getWaitingQueueSize()
            QueueStatus.ACTIVE -> getActiveQueueSize()
            QueueStatus.EXPIRED -> 0L
        }
    }

    override fun findExpiredTokens(): List<QueueTokenModel> {
        // Redis에서 자동으로 만료 처리되므로 빈 리스트 반환
        return emptyList()
    }
}
