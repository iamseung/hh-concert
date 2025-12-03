package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 포인트 잔액 동시성 테스트
 *
 * 목적: 동시 충전/사용 시 잔액 정합성 보장 검증
 * Rule: 최종 잔액 = 초기 + Σ충전 - Σ사용
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("포인트 잔액 동시성 테스트")
class PointConcurrencyTest {

    @Autowired
    private lateinit var pointJpaRepository: PointJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @AfterEach
    fun cleanup() {
        pointJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("동일 사용자 50회 동시 충전 - 최종 잔액 정합성")
    fun `should maintain balance integrity with concurrent charges`() {
        // Given: 초기 잔액 10,000원
        val initialBalance = 10000
        val point = Point(userId = 1L, balance = initialBalance)
        val saved = pointJpaRepository.save(point)

        val chargeCount = 50
        val chargeAmount = 1000
        val expectedBalance = initialBalance + (chargeCount * chargeAmount)

        val executor = Executors.newFixedThreadPool(chargeCount)
        val latch = CountDownLatch(chargeCount)
        val successCount = AtomicInteger(0)

        // When: 50회 동시 충전
        repeat(chargeCount) {
            executor.submit {
                try {
                    transactionTemplate.execute {
                        val locked = pointJpaRepository.findByUserIdWithLock(saved.userId)
                        if (locked != null) {
                            locked.balance += chargeAmount
                            pointJpaRepository.save(locked)
                            successCount.incrementAndGet()
                            Thread.sleep(10)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 50회 모두 성공, 잔액 정합성
        val result = pointJpaRepository.findByUserId(saved.userId)

        assertThat(successCount.get()).isEqualTo(chargeCount)
        assertThat(result?.balance).isEqualTo(expectedBalance)
    }

    @Test
    @DisplayName("충전 20회 + 사용 20회 혼합 - 잔액 정합성")
    fun `should maintain balance with mixed charge and use operations`() {
        // Given: 초기 잔액 50,000원
        val initialBalance = 50000
        val point = Point(userId = 2L, balance = initialBalance)
        val saved = pointJpaRepository.save(point)

        val chargeCount = 20
        val useCount = 20
        val chargeAmount = 1000
        val useAmount = 500

        val totalOps = chargeCount + useCount
        val executor = Executors.newFixedThreadPool(totalOps)
        val latch = CountDownLatch(totalOps)

        val chargeSuccess = AtomicInteger(0)
        val useSuccess = AtomicInteger(0)

        // When: 충전 20회
        repeat(chargeCount) {
            executor.submit {
                try {
                    transactionTemplate.execute {
                        val locked = pointJpaRepository.findByUserIdWithLock(saved.userId)
                        if (locked != null) {
                            locked.balance += chargeAmount
                            pointJpaRepository.save(locked)
                            chargeSuccess.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // When: 사용 20회
        repeat(useCount) {
            executor.submit {
                try {
                    transactionTemplate.execute {
                        val locked = pointJpaRepository.findByUserIdWithLock(saved.userId)
                        if (locked != null && locked.balance >= useAmount) {
                            locked.balance -= useAmount
                            pointJpaRepository.save(locked)
                            useSuccess.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 잔액 정합성
        val result = pointJpaRepository.findByUserId(saved.userId)
        val expectedBalance = initialBalance +
            (chargeSuccess.get() * chargeAmount) -
            (useSuccess.get() * useAmount)

        assertThat(chargeSuccess.get()).isEqualTo(chargeCount)
        assertThat(result?.balance).isEqualTo(expectedBalance)
        assertThat(result?.balance).isGreaterThanOrEqualTo(0)
    }

    @Test
    @DisplayName("잔액 부족 시나리오 - Lost Update 방지")
    fun `should prevent lost update when balance insufficient`() {
        // Given: 초기 잔액 10,000원
        val initialBalance = 10000
        val point = Point(userId = 3L, balance = initialBalance)
        val saved = pointJpaRepository.save(point)

        val useCount = 3
        val useAmount = 6000 // 3명이 각각 6,000원 사용 시도

        val executor = Executors.newFixedThreadPool(useCount)
        val latch = CountDownLatch(useCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 3명이 동시에 6,000원 사용 시도
        repeat(useCount) {
            executor.submit {
                try {
                    transactionTemplate.execute {
                        val locked = pointJpaRepository.findByUserIdWithLock(saved.userId)
                        if (locked != null) {
                            if (locked.balance >= useAmount) {
                                locked.balance -= useAmount
                                pointJpaRepository.save(locked)
                                successCount.incrementAndGet()
                            } else {
                                failCount.incrementAndGet()
                            }
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 1개 성공, 2개 실패
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(2)

        val result = pointJpaRepository.findByUserId(saved.userId)
        assertThat(result?.balance).isEqualTo(initialBalance - useAmount)
    }
}
