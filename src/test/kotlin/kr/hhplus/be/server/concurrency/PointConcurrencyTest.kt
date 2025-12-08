package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 포인트 잔액 동시성 테스트 (Service 계층)
 *
 * 목적: 동시 충전/사용 시 잔액 정합성 보장 검증
 * Rule: 최종 잔액 = 초기 + Σ충전 - Σ사용
 * 전략: 비관적 락을 사용한 동시성 제어 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("포인트 잔액 동시성 테스트")
class PointConcurrencyTest {

    @Autowired
    private lateinit var pointService: PointService

    @Autowired
    private lateinit var pointJpaRepository: PointJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    private lateinit var user: User
    private lateinit var point: Point

    @BeforeEach
    fun setUp() {
        user = User(userName = "test", email = "test@test.com", password = "password")
            .apply { userJpaRepository.save(this) }
        point = Point(userId = user.id, balance = 50000)
            .apply { pointJpaRepository.save(this) }
    }

    @AfterEach
    fun cleanup() {
        pointJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("동일 사용자 50회 동시 충전 - 최종 잔액 정합성")
    fun `should maintain balance integrity with concurrent charges`() {
        // Given
        val chargeCount = 50
        val chargeAmount = 1000
        val expectedBalance = point.balance + (chargeCount * chargeAmount)

        val executor = Executors.newFixedThreadPool(chargeCount)
        val latch = CountDownLatch(chargeCount)
        val successCount = AtomicInteger(0)

        // When: 50회 동시 충전 (Service 계층 테스트)
        repeat(chargeCount) {
            executor.submit {
                runCatching {
                    pointService.chargePoint(user.id, chargeAmount)
                }.onSuccess {
                    successCount.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 50회 모두 성공, 잔액 정합성
        val result = pointService.getPointByUserId(user.id)

        assertThat(successCount.get()).isEqualTo(chargeCount)
        assertThat(result.balance).isEqualTo(expectedBalance)
    }

    @Test
    @DisplayName("충전 20회 + 사용 20회 혼합 - 잔액 정합성")
    fun `should maintain balance with mixed charge and use operations`() {
        // Given
        val chargeCount = 20
        val useCount = 20
        val chargeAmount = 1000
        val useAmount = 500

        val totalOps = chargeCount + useCount
        val executor = Executors.newFixedThreadPool(totalOps)
        val latch = CountDownLatch(totalOps)

        val chargeSuccess = AtomicInteger(0)
        val useSuccess = AtomicInteger(0)

        // When: 충전 20회 (Service 계층 테스트)
        repeat(chargeCount) {
            executor.submit {
                runCatching {
                    pointService.chargePoint(user.id, chargeAmount)
                }.onSuccess {
                    chargeSuccess.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        // When: 사용 20회 (Service 계층 테스트)
        repeat(useCount) {
            executor.submit {
                runCatching {
                    pointService.usePoint(user.id, useAmount)
                }.onSuccess {
                    useSuccess.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 잔액 정합성
        val result = pointService.getPointByUserId(user.id)
        val expectedBalance = point.balance +
            (chargeSuccess.get() * chargeAmount) -
            (useSuccess.get() * useAmount)

        assertThat(chargeSuccess.get()).isEqualTo(chargeCount)
        assertThat(result.balance).isEqualTo(expectedBalance)
        assertThat(result.balance).isGreaterThanOrEqualTo(0)
    }

    @Test
    @DisplayName("잔액 부족 시나리오 - Lost Update 방지")
    fun `should prevent lost update when balance insufficient`() {
        // Given
        val useCount = 3
        val useAmount = 30000 // 3명이 각각 6,000원 사용 시도

        val executor = Executors.newFixedThreadPool(useCount)
        val latch = CountDownLatch(useCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 3명이 동시에 6,000원 사용 시도 (Service 사용)
        repeat(useCount) {
            executor.submit {
                runCatching {
                    pointService.usePoint(user.id, useAmount)
                }.fold(
                    onSuccess = { successCount.incrementAndGet() },
                    onFailure = { failCount.incrementAndGet() }
                ).also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 1개 성공, 2개 실패
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(2)

        val result = pointService.getPointByUserId(user.id)
        assertThat(result.balance).isEqualTo(point.balance - useAmount)
    }
}
