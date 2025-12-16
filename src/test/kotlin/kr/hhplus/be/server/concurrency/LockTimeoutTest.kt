package kr.hhplus.be.server.concurrency

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
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Pessimistic Lock Timeout 테스트
 *
 * 목적: Lock 획득 실패 시 타임아웃과 예외 처리가 제대로 동작하는지 검증
 * 설정: javax.persistence.lock.timeout: 3000 (3초)
 *
 * 시나리오:
 * 1. Lock Timeout 발생 시 예외 확인
 * 2. Timeout 시간 측정 검증
 * 3. 여러 리소스에 대한 Timeout 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Pessimistic Lock Timeout 테스트")
class LockTimeoutTest {

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
    @DisplayName("Lock 타임아웃 발생 시 PessimisticLockingFailureException 또는 CannotAcquireLockException 발생")
    fun `should throw lock exception when timeout occurs`() {
        // Given: 두 개의 스레드가 동일한 Point에 대해 Lock 경쟁
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val thread1Started = AtomicBoolean(false)
        val thread2Exception = AtomicReference<Exception>()

        // Thread 1: Lock을 오래 보유 (5초)
        executor.submit {
            transactionTemplate.execute {
                val locked = pointJpaRepository.findByUserIdWithLock(user.id)
                assertThat(locked).isNotNull
                thread1Started.set(true)
                startLatch.countDown()

                // Lock을 5초간 보유 (타임아웃 3초보다 김)
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        // Thread 1이 Lock을 획득할 때까지 대기
        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(100) // Lock 획득 확실히 하기

        // Thread 2: Lock 획득 시도 (타임아웃 발생 예상)
        executor.submit {
            runCatching {
                transactionTemplate.execute {
                    // Lock 획득 시도 → 3초 타임아웃 예상
                    pointJpaRepository.findByUserIdWithLock(user.id)
                }
            }.onFailure { exception ->
                thread2Exception.set(exception as Exception)
            }
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: Thread 2에서 Lock 관련 예외 발생 확인
        val exception = thread2Exception.get()
        assertThat(exception).isNotNull

        // H2는 JpaSystemException을 던질 수 있음
        assertThat(exception)
            .satisfiesAnyOf(
                { ex -> assertThat(ex).isInstanceOf(PessimisticLockingFailureException::class.java) },
                { ex -> assertThat(ex).isInstanceOf(CannotAcquireLockException::class.java) },
                { ex -> assertThat(ex).isInstanceOf(org.springframework.orm.jpa.JpaSystemException::class.java) },
            )

        println("✅ Lock Timeout Exception: ${exception::class.simpleName}")
        println("   Message: ${exception.message}")
    }

    @Test
    @DisplayName("Lock 타임아웃 시간이 설정된 3초 내외로 발생")
    fun `should timeout around configured 3 seconds`() {
        // Given
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val timeoutDuration = AtomicLong(0)

        // Thread 1: Lock 보유 (5초)
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        // Thread 1이 Lock 획득할 때까지 대기
        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(100)

        // Thread 2: Lock 획득 시도 및 시간 측정
        executor.submit {
            val start = System.currentTimeMillis()
            runCatching {
                transactionTemplate.execute {
                    pointJpaRepository.findByUserIdWithLock(user.id)
                }
            }.onFailure {
                val duration = System.currentTimeMillis() - start
                timeoutDuration.set(duration)
            }
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 타임아웃이 2~4초 사이에 발생 (H2는 MySQL과 다를 수 있음)
        val actualTimeout = timeoutDuration.get()
        assertThat(actualTimeout).isGreaterThan(1500) // 최소 1.5초
        assertThat(actualTimeout).isLessThan(4500) // 최대 4.5초

        println("✅ Lock Timeout Duration: ${actualTimeout}ms (expected ~2000-3000ms)")
        println("   Note: H2 may have different timeout behavior than MySQL")
    }

    @Test
    @DisplayName("Lock 획득 성공 시에는 타임아웃 없이 정상 동작")
    fun `should work normally when lock is available`() {
        // Given: Lock이 사용 가능한 상태

        // When: Lock 획득 시도 및 시간 측정
        val start = System.currentTimeMillis()
        val result = transactionTemplate.execute {
            pointJpaRepository.findByUserIdWithLock(user.id)
        }
        val duration = System.currentTimeMillis() - start

        // Then: 즉시 획득 (100ms 이내)
        assertThat(result).isNotNull
        assertThat(duration).isLessThan(100)

        println("✅ Lock Acquired Immediately: ${duration}ms")
    }

    @Test
    @DisplayName("여러 스레드가 순차적으로 Lock 획득 시 각각 타임아웃 없이 성공")
    fun `should acquire lock sequentially without timeout`() {
        // Given: 5개의 스레드가 순차적으로 Lock 획득
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicBoolean(true)
        val durations = mutableListOf<Long>()

        // When: 각 스레드가 짧은 시간 동안만 Lock 보유
        repeat(threadCount) { index ->
            executor.submit {
                val start = System.currentTimeMillis()
                runCatching {
                    transactionTemplate.execute {
                        val locked = pointJpaRepository.findByUserIdWithLock(user.id)
                        assertThat(locked).isNotNull
                        // 짧은 작업 시뮬레이션 (500ms)
                        Thread.sleep(500)
                    }
                }.onSuccess {
                    val duration = System.currentTimeMillis() - start
                    synchronized(durations) {
                        durations.add(duration)
                    }
                }.onFailure {
                    successCount.set(false)
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 모든 스레드가 성공적으로 Lock 획득
        assertThat(successCount.get()).isTrue()
        assertThat(durations).hasSize(threadCount)

        // 각 스레드의 대기 시간 출력
        durations.forEachIndexed { index, duration ->
            println("Thread $index: ${duration}ms")
            // 타임아웃(3000ms)보다 훨씬 짧아야 함
            assertThat(duration).isLessThan(3000)
        }
    }

    @Test
    @DisplayName("동시에 많은 스레드가 Lock 경쟁 시 타임아웃 발생 확인")
    fun `should have timeouts when many threads compete for lock`() {
        // Given: 10개의 스레드가 동시에 Lock 경쟁
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicBoolean(false)
        val timeoutCount = AtomicBoolean(false)

        // When: 모든 스레드가 동시에 시작
        repeat(threadCount) { index ->
            executor.submit {
                // 모든 스레드가 동시에 시작하도록 대기
                startLatch.await()

                runCatching {
                    transactionTemplate.execute {
                        pointJpaRepository.findByUserIdWithLock(user.id)
                        // Lock을 오래 보유 (2초)
                        Thread.sleep(2000)
                    }
                }.onSuccess {
                    successCount.set(true)
                }.onFailure { exception ->
                    if (exception is PessimisticLockingFailureException ||
                        exception is CannotAcquireLockException ||
                        exception is org.springframework.orm.jpa.JpaSystemException
                    ) {
                        timeoutCount.set(true)
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

        // Then: 일부는 성공, 일부는 타임아웃 발생
        assertThat(successCount.get()).isTrue()
        assertThat(timeoutCount.get()).isTrue()

        println("✅ Some threads succeeded, some timed out (as expected)")
    }
}
