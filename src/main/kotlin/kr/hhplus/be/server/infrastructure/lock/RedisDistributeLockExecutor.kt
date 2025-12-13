package kr.hhplus.be.server.infrastructure.lock

import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.common.exception.LockAcquisitionException
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redisson 기반 분산 락
 *
 * Redis를 이용한 분산 락을 제공하며, 다음과 같은 특징이 있습니다:
 * - tryLock: 락 획득을 대기하며, waitMilliSeconds 동안 시도
 * - leaseMilliSeconds: 락이 자동으로 해제되는 시간 (데드락 방지)
 *
 */
@Component
class RedisDistributeLockExecutor(
    private val redissonClient: RedissonClient,
) : DistributeLockExecutor {

    private val logger = LoggerFactory.getLogger(RedisDistributeLockExecutor::class.java)

    override fun <T> execute(
        lockKey: String,
        waitMilliSeconds: Long,
        leaseMilliSeconds: Long,
        logic: () -> T,
    ): T {
        require(waitMilliSeconds >= 0) { "waitMilliSeconds must be non-negative" }
        require(leaseMilliSeconds > 0) { "leaseMilliSeconds must be positive" }

        val lock = redissonClient.getLock(lockKey)
        val acquired = acquireLock(lock, lockKey, waitMilliSeconds, leaseMilliSeconds)

        return try {
            logic()
        } finally {
            releaseLock(lock, lockKey, acquired)
        }
    }

    private fun acquireLock(
        lock: RLock,
        lockKey: String,
        waitMilliSeconds: Long,
        leaseMilliSeconds: Long,
    ): Boolean {
        return try {
            lock.tryLock(waitMilliSeconds, leaseMilliSeconds, TimeUnit.MILLISECONDS).also { acquired ->
                if (!acquired) {
                    logger.warn("Failed to acquire lock: lockKey=$lockKey")
                    throw LockAcquisitionException(ErrorCode.LOCK_ACQUISITION_FAILED, lockKey)
                }
                logger.debug("Lock acquired: lockKey=$lockKey")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw LockAcquisitionException(ErrorCode.LOCK_INTERRUPTED, lockKey)
        }
    }

    private fun releaseLock(lock: RLock, lockKey: String, acquired: Boolean) {
        if (acquired && lock.isHeldByCurrentThread) {
            runCatching { lock.unlock() }
                .onSuccess { logger.debug("Lock released: lockKey=$lockKey") }
                .onFailure { logger.warn("Failed to release lock: lockKey=$lockKey", it) }
        }
    }
}
