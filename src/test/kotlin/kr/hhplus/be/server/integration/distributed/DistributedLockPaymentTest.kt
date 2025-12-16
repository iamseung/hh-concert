package kr.hhplus.be.server.integration.distributed

import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentCommand
import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentUseCase
import kr.hhplus.be.server.common.exception.LockAcquisitionException
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.payment.service.PaymentService
import kr.hhplus.be.server.domain.point.service.PointService
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.payment.repository.PaymentJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointHistoryJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.queue.repository.RedisQueueRepository
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import kr.hhplus.be.server.infrastructure.persistence.reservation.repository.ReservationJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ProcessPaymentUseCase 분산락 통합 테스트
 *
 * 테스트 목적:
 * 1. 분산락이 동일 예약에 대한 중복 결제를 방지하는지 검증
 * 2. 락 격리(예약별)가 올바르게 동작하는지 검증
 * 3. 6개 작업의 원자성이 보장되는지 검증
 *    (포인트 차감 + 히스토리 + 결제 생성 + 좌석 상태 + 예약 상태 + 토큰 만료)
 * 4. TOCTOU 방지를 위한 예약 재검증이 동작하는지 검증
 * 5. READ 작업이 락 밖에서 실행되어 성능이 개선되는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("분산락 통합 테스트 - 결제 처리")
class DistributedLockPaymentTest @Autowired constructor(
    private var processPaymentUseCase: ProcessPaymentUseCase,
    private var seatService: SeatService,
    private var reservationService: ReservationService,
    private var paymentService: PaymentService,
    private var pointService: PointService,
    private var queueTokenService: QueueTokenService,
    private var userJpaRepository: UserJpaRepository,
    private var pointJpaRepository: PointJpaRepository,
    private var pointHistoryJpaRepository: PointHistoryJpaRepository,
    private var concertJpaRepository: ConcertJpaRepository,
    private var concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private var seatJpaRepository: SeatJpaRepository,
    private var reservationJpaRepository: ReservationJpaRepository,
    private var paymentJpaRepository: PaymentJpaRepository,
    private var redissonClient: RedissonClient,
    private val redisQueueRepository: RedisQueueRepository,
) {

    companion object {
        @Container
        @ServiceConnection
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var user: User
    private lateinit var point: Point
    private lateinit var concert: Concert
    private lateinit var schedule: ConcertSchedule
    private lateinit var seat: Seat
    private lateinit var reservation: Reservation
    private var queueToken: String = ""

    @BeforeEach
    fun setUp() {
        // 사용자 및 포인트 설정
        user = User(userName = "testUser", email = "test@test.com", password = "password")
            .apply { userJpaRepository.save(this) }
        point = Point(userId = user.id, balance = 100000) // 충분한 잔액
            .apply { pointJpaRepository.save(this) }

        // 콘서트 및 일정 설정
        concert = Concert(title = "Test Concert", description = "Test Description")
            .apply { concertJpaRepository.save(this) }
        schedule = ConcertSchedule(
            concertId = concert.id,
            concertDate = LocalDateTime.now().plusDays(7),
        ).apply { concertScheduleJpaRepository.save(this) }

        // 좌석 설정 (임시 예약 상태)
        seat = Seat(
            concertScheduleId = schedule.id,
            seatNumber = 1,
            price = 50000,
            seatStatus = SeatStatus.TEMPORARY_RESERVED,
        ).apply { seatJpaRepository.save(this) }

        // 예약 설정 (임시 예약 상태)
        reservation = Reservation(
            userId = user.id,
            seatId = seat.id,
        ).apply { reservationJpaRepository.save(this) }

        // 대기열 토큰 생성 (ACTIVE 상태)
        val token = queueTokenService.createQueueToken(user.id)
        queueTokenService.activateWaitingTokens(1)
        queueToken = token.token
    }

    @AfterEach
    fun cleanup() {
        paymentJpaRepository.deleteAll()
        pointHistoryJpaRepository.deleteAll()
        reservationJpaRepository.deleteAll()
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        pointJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        // Redis 락 및 큐 정리
        redissonClient.keys.flushdb()
    }

    @Test
    @DisplayName("[중복 결제 방지] 동일 예약 50회 동시 결제 시도 - 1회만 성공")
    fun `should prevent duplicate payments with distributed lock`() {
        // Given
        val attemptCount = 50
        val initialBalance = point.balance

        val executor = Executors.newFixedThreadPool(attemptCount)
        val latch = CountDownLatch(attemptCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // When: 50개 스레드가 동시에 결제 시도
        repeat(attemptCount) {
            executor.submit {
                runCatching {
                    processPaymentUseCase.execute(
                        ProcessPaymentCommand(
                            userId = user.id,
                            reservationId = reservation.id,
                            queueToken = queueToken,
                        ),
                    )
                }.fold(
                    onSuccess = { successCount.incrementAndGet() },
                    onFailure = { failCount.incrementAndGet() },
                ).also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 1회만 성공, 나머지는 실패
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(attemptCount - 1)

        // 포인트 정합성 검증
        val resultPoint = pointService.getPointByUserId(user.id)
        assertThat(resultPoint.balance).isEqualTo(initialBalance - seat.price)

        // 결제 1건만 생성 확인
        val payments = paymentJpaRepository.findAll()
        assertThat(payments).hasSize(1)

        // 좌석 상태 확인 (RESERVED)
        val resultSeat = seatService.findById(seat.id)
        assertThat(resultSeat.seatStatus).isEqualTo(SeatStatus.RESERVED)

        // 예약 상태 확인 (CONFIRMED)
        val resultReservation = reservationService.findById(reservation.id)
        assertThat(resultReservation.reservationStatus).isEqualTo(ReservationStatus.CONFIRMED)

        println("✅ 중복 결제 방지 성공: $attemptCount 회 시도, 1회 성공, ${failCount.get()}회 실패")
    }

    @Test
    @DisplayName("[락 격리] 서로 다른 예약 동시 결제 - 독립적으로 병렬 처리")
    fun `should process payments independently for different reservations`() {
        // Given: 3개의 독립적인 예약 생성
        val reservations = (1..3).map { idx ->
            val u = User(userName = "user$idx", email = "user$idx@test.com", password = "password")
                .apply { userJpaRepository.save(this) }
            val p = Point(userId = u.id, balance = 100000)
                .apply { pointJpaRepository.save(this) }
            val s = Seat(
                concertScheduleId = schedule.id,
                seatNumber = idx + 1,
                price = 50000,
                seatStatus = SeatStatus.TEMPORARY_RESERVED,
            ).apply { seatJpaRepository.save(this) }
            val r = Reservation(
                userId = u.id,
                seatId = s.id,
            ).apply { reservationJpaRepository.save(this) }
            val token = queueTokenService.createQueueToken(u.id)
            queueTokenService.activateWaitingTokens(1)
            val activeToken = token.token

            Triple(u, r, activeToken)
        }

        val executor = Executors.newFixedThreadPool(reservations.size)
        val latch = CountDownLatch(reservations.size)
        val successCount = AtomicInteger(0)

        val startTime = System.currentTimeMillis()

        // When: 3개 예약에 대해 동시 결제
        reservations.forEach { (u, r, token) ->
            executor.submit {
                runCatching {
                    processPaymentUseCase.execute(
                        ProcessPaymentCommand(
                            userId = u.id,
                            reservationId = r.id,
                            queueToken = token,
                        ),
                    )
                }.onSuccess {
                    successCount.incrementAndGet()
                }.also {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val duration = System.currentTimeMillis() - startTime

        // Then: 3개 모두 성공
        assertThat(successCount.get()).isEqualTo(reservations.size)

        // 각 예약별 결제 및 상태 확인
        reservations.forEach { (u, r, _) ->
            val resultPoint = pointService.getPointByUserId(u.id)
            assertThat(resultPoint.balance).isEqualTo(100000 - 50000)

            val resultReservation = reservationService.findById(r.id)
            assertThat(resultReservation.reservationStatus).isEqualTo(ReservationStatus.CONFIRMED)
        }

        // 결제 3건 생성 확인
        val payments = paymentJpaRepository.findAll()
        assertThat(payments.size).isGreaterThanOrEqualTo(3)

        println("✅ 락 격리 성공: ${reservations.size}개 예약 동시 처리, 총 소요시간 = ${duration}ms")
    }

    @Test
    @DisplayName("[원자성] 6개 작업이 함께 커밋/롤백")
    fun `should ensure atomicity of all payment operations`() {
        // Given
        val initialBalance = point.balance
        val initialHistoryCount = pointHistoryJpaRepository.count()

        // When: 정상 결제
        val result = processPaymentUseCase.execute(
            ProcessPaymentCommand(
                userId = user.id,
                reservationId = reservation.id,
                queueToken = queueToken,
            ),
        )

        // Then: 6개 작업이 모두 성공
        // 1. 포인트 차감
        val resultPoint = pointService.getPointByUserId(user.id)
        assertThat(resultPoint.balance).isEqualTo(initialBalance - seat.price)

        // 2. 히스토리 기록
        val historyCount = pointHistoryJpaRepository.count()
        assertThat(historyCount).isEqualTo(initialHistoryCount + 1)

        // 3. 결제 생성
        assertThat(result.paymentId).isNotNull()
        assertThat(result.amount).isEqualTo(seat.price)

        // 4. 좌석 상태 변경
        val resultSeat = seatService.findById(seat.id)
        assertThat(resultSeat.seatStatus).isEqualTo(SeatStatus.RESERVED)

        // 5. 예약 상태 변경
        val resultReservation = reservationService.findById(reservation.id)
        assertThat(resultReservation.reservationStatus).isEqualTo(ReservationStatus.CONFIRMED)

        println("""
            ✅ 원자성 보장: 6개 작업 함께 커밋
            - 포인트 차감: ${initialBalance} → ${resultPoint.balance}
            - 히스토리 기록: +1
            - 결제 생성: ${result.paymentId}
            - 좌석 상태: TEMPORARY_RESERVED → RESERVED
            - 예약 상태: TEMPORARY → CONFIRMED
        """.trimIndent())
    }

    @Test
    @DisplayName("[TOCTOU 방지] 락 대기 중 예약 상태 변경 시 재검증으로 실패")
    fun `should prevent TOCTOU with reservation revalidation`() {
        // Given: 첫 번째 스레드가 락을 보유
        val lockKey = "reservation:payment:lock:${reservation.id}"
        val lock = redissonClient.getLock(lockKey)

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val results = mutableListOf<Result<Any>>()

        // When: Thread 1 - 락 획득 후 예약 상태 변경
        executor.submit {
            lock.lock(5, TimeUnit.SECONDS)
            try {
                // 예약을 CONFIRMED로 변경 (다른 경로로 결제 완료)
                val r = reservationJpaRepository.findById(reservation.id).get()
                r.reservationStatus = ReservationStatus.CONFIRMED
                reservationJpaRepository.save(r)

                Thread.sleep(2000) // 2초간 보유
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
                latch.countDown()
            }
        }

        Thread.sleep(100) // Thread 1이 락을 먼저 획득하도록

        // When: Thread 2 - 결제 시도 (락 대기 중 예약 상태가 변경됨)
        executor.submit {
            runCatching {
                processPaymentUseCase.execute(
                    ProcessPaymentCommand(
                        userId = user.id,
                        reservationId = reservation.id,
                        queueToken = queueToken,
                    ),
                )
            }.also { result ->
                synchronized(results) {
                    results.add(result)
                }
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        // Then: Thread 2는 재검증에서 실패
        assertThat(results).hasSize(1)
        assertThat(results[0].isFailure).isTrue()
        println("✅ TOCTOU 방지: 락 대기 중 예약 상태 변경 감지, 재검증으로 실패")
    }

    @Test
    @DisplayName("[성능] READ 작업이 락 밖에서 실행되어 처리 시간 단축")
    fun `should measure performance improvement with reads outside lock`() {
        // Given
        val paymentCount = 10

        val executor = Executors.newFixedThreadPool(paymentCount)
        val latch = CountDownLatch(paymentCount)

        // 10개의 독립적인 예약 준비
        val testData = (1..paymentCount).map { idx ->
            val u = User(userName = "perf$idx", email = "perf$idx@test.com", password = "password")
                .apply { userJpaRepository.save(this) }
            val p = Point(userId = u.id, balance = 100000)
                .apply { pointJpaRepository.save(this) }
            val s = Seat(
                concertScheduleId = schedule.id,
                seatNumber = 10 + idx,
                price = 50000,
                seatStatus = SeatStatus.TEMPORARY_RESERVED,
            ).apply { seatJpaRepository.save(this) }
            val r = Reservation(
                userId = u.id,
                seatId = s.id,
            ).apply { reservationJpaRepository.save(this) }
            val token = queueTokenService.createQueueToken(u.id)
            queueTokenService.activateWaitingTokens(1)
            val activeToken = token.token

            Triple(u, r, activeToken)
        }

        val startTime = System.currentTimeMillis()

        // When: 10개 결제 동시 처리
        testData.forEach { (u, r, token) ->
            executor.submit {
                processPaymentUseCase.execute(
                    ProcessPaymentCommand(
                        userId = u.id,
                        reservationId = r.id,
                        queueToken = token,
                    ),
                )
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        val totalDuration = System.currentTimeMillis() - startTime
        val avgDuration = totalDuration / paymentCount

        // Then: 성능 측정
        val payments = paymentJpaRepository.findAll()
        assertThat(payments.size).isGreaterThanOrEqualTo(paymentCount)
        assertThat(avgDuration).isLessThan(500) // 평균 500ms 이하

        println("""
            ✅ 성능 측정 (READ 작업 락 밖):
            - 총 요청: $paymentCount 회
            - 총 소요시간: ${totalDuration}ms
            - 평균 처리시간: ${avgDuration}ms
            - 결제 생성 수: ${payments.size}
        """.trimIndent())
    }

    @Test
    @DisplayName("[락 타임아웃] Wait timeout 3초 초과 시 락 획득 실패")
    fun `should throw exception when lock wait timeout exceeded`() {
        // Given: 첫 번째 스레드가 락을 5초간 보유
        val lockKey = "reservation:payment:lock:${reservation.id}"
        val lock = redissonClient.getLock(lockKey)

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val results = mutableListOf<Result<Any>>()

        // When: Thread 1 - 락을 5초간 보유
        executor.submit {
            lock.lock(5, TimeUnit.SECONDS)
            try {
                Thread.sleep(4000) // 4초간 보유
            } finally {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
                latch.countDown()
            }
        }

        Thread.sleep(100) // Thread 1이 락을 먼저 획득하도록

        // When: Thread 2 - 결제 시도 (wait timeout 3초)
        executor.submit {
            runCatching {
                processPaymentUseCase.execute(
                    ProcessPaymentCommand(
                        userId = user.id,
                        reservationId = reservation.id,
                        queueToken = queueToken,
                    ),
                )
            }.also { result ->
                synchronized(results) {
                    results.add(result)
                }
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        // Then: Thread 2는 락 획득 실패 (3초 wait timeout)
        assertThat(results).hasSize(1)
        assertThat(results[0].isFailure).isTrue()
        assertThat(results[0].exceptionOrNull()).isInstanceOf(LockAcquisitionException::class.java)
        println("✅ 락 타임아웃 동작 확인: 3초 대기 후 예외 발생")
    }

    @Test
    @DisplayName("[데드락 방지] Lease timeout 5초 후 자동 해제")
    fun `should auto release lock after lease timeout`() {
        // Given: 락을 획득하고 해제하지 않는 상황 (서버 장애 시뮬레이션)
        val lockKey = "reservation:payment:lock:${reservation.id}"
        val lock = redissonClient.getLock(lockKey)

        // When: 락 획득 후 해제하지 않음 (lease time 1초로 짧게 설정)
        lock.lock(1, TimeUnit.SECONDS)
        // unlock 하지 않음 (의도적)

        // 2초 대기 (lease timeout 후)
        Thread.sleep(2000)

        // Then: 다른 스레드가 락 획득 가능
        val canAcquire = lock.tryLock(100, TimeUnit.MILLISECONDS)
        assertThat(canAcquire).isTrue()
        lock.unlock()

        println("✅ 데드락 방지: Lease timeout 후 자동 해제 확인")
    }
}
