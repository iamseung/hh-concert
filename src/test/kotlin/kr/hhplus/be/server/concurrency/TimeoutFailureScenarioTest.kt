package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.application.usecase.point.ChargePointCommand
import kr.hhplus.be.server.application.usecase.point.ChargePointUseCase
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.util.concurrent.atomic.AtomicReference

/**
 * 타임아웃 최종 실패 시나리오 테스트
 *
 * 목적: 모든 재시도가 실패했을 때의 동작 검증
 * - 예외가 올바르게 전파되는지
 * - 데이터 일관성이 유지되는지 (트랜잭션 롤백)
 * - 에러 메시지가 명확한지
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("타임아웃 최종 실패 시나리오 테스트")
class TimeoutFailureScenarioTest {

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
        // Lock 관련 테스트 후에는 cleanup 시 예외가 발생할 수 있음
        runCatching {
            pointJpaRepository.deleteAll()
        }
        runCatching {
            userJpaRepository.deleteAll()
        }
    }

    @Test
    @DisplayName("최종 실패 시 예외 전파 및 명확한 에러 메시지")
    fun `should propagate exception with clear message after max retries`() {
        // Given: Lock을 오래 보유하는 트랜잭션
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        val caughtException = AtomicReference<Exception>()

        // Thread 1: Lock을 5초간 보유
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2: UseCase 호출 (최종 실패 예상)
        executor.submit {
            runCatching {
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }.onFailure { exception ->
                caughtException.set(exception as Exception)
            }
            latch.countDown()
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: Lock 관련 예외 발생
        val exception = caughtException.get()
        assertThat(exception).isNotNull
        assertThat(exception)
            .satisfiesAnyOf(
                { ex -> assertThat(ex).isInstanceOf(PessimisticLockingFailureException::class.java) },
                { ex -> assertThat(ex).isInstanceOf(CannotAcquireLockException::class.java) },
                { ex -> assertThat(ex).isInstanceOf(org.springframework.orm.jpa.JpaSystemException::class.java) },
            )

        // 에러 메시지 확인
        println("✅ Exception Type: ${exception::class.simpleName}")
        println("   Message: ${exception.message}")
        println("   Cause: ${exception.cause?.message}")

        assertThat(exception.message).isNotNull()
    }

    @Test
    @DisplayName("최종 실패 시 트랜잭션 롤백으로 데이터 일관성 유지")
    fun `should rollback transaction and maintain data consistency on final failure`() {
        // Given: 초기 잔액 확인
        val initialBalance = point.balance

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        // Thread 1: Lock 보유
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2: 충전 시도 (실패 예상)
        executor.submit {
            runCatching {
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }
            latch.countDown()
        }

        latch.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 잔액이 변경되지 않음 (롤백됨)
        val finalPoint = pointJpaRepository.findByUserId(user.id)
        assertThat(finalPoint?.balance).isEqualTo(initialBalance)

        println("✅ Transaction rolled back, balance unchanged")
        println("   Initial: $initialBalance, Final: ${finalPoint?.balance}")
    }

    @Test
    @DisplayName("동시 다중 실패 시 모든 트랜잭션 롤백")
    fun `should rollback all failed transactions in concurrent scenario`() {
        // Given: 초기 잔액
        val initialBalance = point.balance
        val threadCount = 5

        val executor = Executors.newFixedThreadPool(threadCount + 1)
        val latch = CountDownLatch(threadCount + 1)
        val startLatch = CountDownLatch(1)

        // Thread 1: Lock을 오래 보유
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(8000) // 8초간 보유
            }
            latch.countDown()
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // Thread 2-6: 충전 시도 (모두 실패 예상)
        repeat(threadCount) {
            executor.submit {
                runCatching {
                    chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
                }
                latch.countDown()
            }
        }

        latch.await(20, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 잔액 변경 없음 (모든 실패한 트랜잭션 롤백)
        val finalPoint = pointJpaRepository.findByUserId(user.id)
        assertThat(finalPoint?.balance).isEqualTo(initialBalance)

        println("✅ All failed transactions rolled back")
        println("   Balance remained: ${finalPoint?.balance}")
    }

    @Test
    @DisplayName("재시도 실패 후 즉시 다시 시도하면 성공")
    fun `should succeed on immediate retry after failure`() {
        // Given: Lock을 중간 시간 동안 보유
        val executor = Executors.newFixedThreadPool(2)
        val latch1 = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        // Phase 1: Lock 경쟁으로 실패
        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(7000) // 7초 보유
            }
            latch1.countDown()
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        var firstAttemptFailed = false
        executor.submit {
            runCatching {
                chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
            }.onFailure {
                firstAttemptFailed = true
            }
            latch1.countDown()
        }

        latch1.await(15, TimeUnit.SECONDS)
        executor.shutdown()

        // 첫 번째 시도 실패 확인
        assertThat(firstAttemptFailed).isTrue()

        // Phase 2: Lock이 해제된 후 다시 시도 → 성공
        Thread.sleep(500) // Lock 완전히 해제될 때까지 대기

        val secondAttempt = chargePointUseCase.execute(
            ChargePointCommand(userId = user.id, amount = 1000),
        )

        assertThat(secondAttempt.balance).isEqualTo(51000)

        println("✅ Second attempt succeeded after first failure")
    }

    @Test
    @DisplayName("UseCase에서 예외 발생 시 적절한 예외 타입 반환")
    fun `should throw appropriate exception type from UseCase`() {
        // Given: Lock 보유
        val executor = Executors.newFixedThreadPool(1)
        val startLatch = CountDownLatch(1)

        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(5000)
            }
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // When & Then: UseCase 호출 시 예외 타입 확인
        assertThatThrownBy {
            chargePointUseCase.execute(ChargePointCommand(userId = user.id, amount = 1000))
        }.satisfiesAnyOf(
            { ex -> assertThat(ex).isInstanceOf(PessimisticLockingFailureException::class.java) },
            { ex -> assertThat(ex).isInstanceOf(CannotAcquireLockException::class.java) },
            { ex -> assertThat(ex).isInstanceOf(org.springframework.orm.jpa.JpaSystemException::class.java) },
        )

        executor.shutdown()

        println("✅ Correct exception type thrown from UseCase")
    }

    @Test
    @DisplayName("타임아웃 설정 시간 내에 Lock 획득 실패 시 즉시 예외 발생")
    fun `should fail fast within timeout period`() {
        // Given: Lock 보유
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)

        executor.submit {
            transactionTemplate.execute {
                pointJpaRepository.findByUserIdWithLock(user.id)
                startLatch.countDown()
                Thread.sleep(5000)
            }
            latch.countDown()
        }

        startLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(50)

        // When: Lock 획득 시도 및 시간 측정
        var attemptDuration = 0L
        executor.submit {
            val start = System.currentTimeMillis()
            runCatching {
                transactionTemplate.execute {
                    pointJpaRepository.findByUserIdWithLock(user.id)
                }
            }
            attemptDuration = System.currentTimeMillis() - start
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Then: 타임아웃 시간 내에 실패 (H2: ~2초, MySQL: ~3초)
        assertThat(attemptDuration).isGreaterThan(1500) // 최소 1.5초
        assertThat(attemptDuration).isLessThan(4000) // 최대 4초

        println("✅ Failed fast within timeout period: ${attemptDuration}ms")
        println("   Note: H2 (~2s) may differ from MySQL (~3s)")
    }
}
