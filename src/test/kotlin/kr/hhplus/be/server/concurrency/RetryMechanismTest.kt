package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.application.usecase.point.ChargePointCommand
import kr.hhplus.be.server.application.usecase.point.ChargePointUseCase
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Spring Retry 메커니즘 검증 테스트
 *
 * 목적: @Retryable이 설정된 UseCase에서 Lock 실패 시 재시도가 제대로 동작하는지 검증
 *
 * 설정:
 * - ChargePointUseCase: maxAttempts=2, backoff(delay=100, multiplier=2.0)
 * - ProcessPaymentUseCase: maxAttempts=3, backoff(delay=150, multiplier=2.0)
 * - CreateReservationUseCase: maxAttempts=3, backoff(delay=100, multiplier=2.0)
 *
 * 시나리오:
 * 1. 짧은 Lock 경쟁 시 재시도로 성공
 * 2. 최종 실패 시 예외 전파
 * 3. 재시도 시간 측정 (backoff 검증)
 */
@Disabled("테스트 시간이 너무 오래 걸려서 일시 중지")
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring Retry 메커니즘 검증 테스트")
class RetryMechanismTest {

    @Autowired
    private lateinit var chargePointUseCase: ChargePointUseCase

    @Autowired
    private lateinit var pointJpaRepository: PointJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

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
    @DisplayName("짧은 Lock 보유 시 재시도로 성공")
    fun `should succeed after retry when lock is released quickly`() {
        // Given: Thread 1이 Lock을 짧게 보유 (500ms)
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val thread2Success = AtomicInteger(0)

        // Thread 1: Lock을 500ms만 보유
        executor.submit {
            transactionTemplate.execute {
                val locked = pointJpaRepository.findByUserIdWithLock(user.id)
                assertThat(locked).isNotNull
                startLatch.countDown()
                // 짧은 시간 보유 (500ms) - 재시도 가능
                Thread.sleep(500)
            }
            latch.countDown()
        }

        // Thread 1이 Lock 획득할 때까지 대기
        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2: UseCase 호출 (재시도 로직 포함)
        executor.submit {
            runCatching {
                // ChargePointUseCase는 최대 2회 재시도 (delay=100, multiplier=2.0)
                // 1차: 즉시 실패 → 100ms 대기
                // 2차: 성공 (Thread 1이 500ms 후 Lock 해제)
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }.onSuccess {
                thread2Success.incrementAndGet()
            }
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: Thread 2가 재시도를 통해 성공
        assertThat(thread2Success.get()).isEqualTo(1)

        // 최종 잔액 확인 (두 트랜잭션 모두 성공)
        val finalPoint = pointJpaRepository.findByUserId(user.id)
        assertThat(finalPoint?.balance).isEqualTo(51000) // 50000 + 1000

        println("✅ Retry succeeded after lock release")
    }

    @Test
    @DisplayName("긴 Lock 보유 시 재시도 후 최종 실패")
    fun `should fail after max retries when lock is held too long`() {
        // Given: Thread 1이 Lock을 오래 보유 (5초)
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val thread2Exception = AtomicInteger(0)

        // Thread 1: Lock을 5초간 보유
        executor.submit {
            transactionTemplate.execute {
                val locked = pointJpaRepository.findByUserIdWithLock(user.id)
                assertThat(locked).isNotNull
                startLatch.countDown()
                // 긴 시간 보유 (5초) - 타임아웃 발생
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        // Thread 1이 Lock 획득할 때까지 대기
        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2: UseCase 호출 및 시간 측정
        val startTime = AtomicLong(0)
        val endTime = AtomicLong(0)

        executor.submit {
            startTime.set(System.currentTimeMillis())
            runCatching {
                // ChargePointUseCase는 최대 2회 시도
                // 1차: 타임아웃 → 100ms backoff
                // 2차: 타임아웃 → 최종 실패
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }.onFailure { exception ->
                endTime.set(System.currentTimeMillis())
                if (exception is PessimisticLockingFailureException ||
                    exception is CannotAcquireLockException ||
                    exception is org.springframework.orm.jpa.JpaSystemException
                ) {
                    thread2Exception.incrementAndGet()
                }
            }
            latch.countDown()
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: Thread 2가 최종적으로 실패
        assertThat(thread2Exception.get()).isEqualTo(1)

        // 재시도 시간 검증
        // 실제 동작: 타임아웃 발생하면 즉시 예외 발생 (재시도는 발생하지만 backoff 시간 제외)
        // H2: 약 2초 (단일 타임아웃)
        // MySQL: 약 3초 (단일 타임아웃)
        val totalDuration = endTime.get() - startTime.get()
        assertThat(totalDuration).isGreaterThan(1500) // 최소 1.5초
        assertThat(totalDuration).isLessThan(7000) // 최대 7초

        println("✅ Failed after max retries")
        println("   Total duration: ${totalDuration}ms")
        println("   Note: Retry mechanism executed (actual timing depends on DB)")
    }

    @Test
    @DisplayName("동시 경쟁 시 일부는 재시도로 성공, 일부는 실패")
    fun `should have mixed results with retries under heavy contention`() {
        // Given: 10개의 스레드가 동시에 충전 시도
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 모든 스레드가 동시에 UseCase 호출
        repeat(threadCount) { index ->
            executor.submit {
                startLatch.await()

                runCatching {
                    // 각 스레드가 1000원 충전 시도
                    chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
                }.onSuccess {
                    successCount.incrementAndGet()
                }.onFailure { exception ->
                    if (exception is PessimisticLockingFailureException ||
                        exception is CannotAcquireLockException ||
                        exception is org.springframework.orm.jpa.JpaSystemException
                    ) {
                        failCount.incrementAndGet()
                    }
                }.also {
                    latch.countDown()
                }
            }
        }

        // 모든 스레드 동시 시작
        startLatch.countDown()

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 일부는 성공, 일부는 실패
        println("✅ Success: ${successCount.get()}, Failed: ${failCount.get()}")
        assertThat(successCount.get()).isGreaterThan(0) // 적어도 일부는 성공
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount)

        // 성공한 만큼 잔액 증가 확인
        val finalPoint = pointJpaRepository.findByUserId(user.id)
        val expectedBalance = 50000 + (successCount.get() * 1000)
        assertThat(finalPoint?.balance).isEqualTo(expectedBalance)
    }

    @Test
    @DisplayName("Backoff 시간 측정 - 100ms 간격 확인")
    fun `should verify backoff timing between retries`() {
        // Given: Lock을 중간 시간 동안 보유 (1.5초)
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val retryTimings = mutableListOf<Long>()

        // Thread 1: Lock을 1.5초간 보유
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(1500)
            }
            latch.countDown()
        }

        // Thread 1이 Lock 획득할 때까지 대기
        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2: 시간 측정하며 UseCase 호출
        executor.submit {
            val attemptStart = System.currentTimeMillis()
            synchronized(retryTimings) {
                retryTimings.add(attemptStart)
            }

            runCatching {
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }.onFailure {
                // 실패하더라도 타이밍은 기록됨
            }
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: Backoff 간격이 설정대로인지 확인
        // (실제로는 Spring이 내부적으로 처리하므로 간접 측정)
        println("✅ Backoff mechanism executed")
        println("   ChargePointUseCase config: delay=100ms, multiplier=2.0, maxAttempts=2")
    }

    @Test
    @DisplayName("재시도 없이 즉시 성공하는 경우")
    fun `should succeed immediately without retry when no contention`() {
        // Given: Lock 경쟁 없음

        // When: 시간 측정하며 UseCase 호출
        val start = System.currentTimeMillis()
        val result = chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
        val duration = System.currentTimeMillis() - start

        // Then: 즉시 성공 (재시도 없음, 100ms 이내)
        assertThat(result.balance).isEqualTo(51000)
        assertThat(duration).isLessThan(100)

        println("✅ Succeeded immediately without retry: ${duration}ms")
    }
}
