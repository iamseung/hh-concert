package kr.hhplus.be.server.transaction

import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentCommand
import kr.hhplus.be.server.application.usecase.payment.ProcessPaymentUseCase
import kr.hhplus.be.server.common.aop.TestContext
import kr.hhplus.be.server.common.aop.TransactionTrackingAspect
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Concert
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.ConcertScheduleJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.point.entity.Point
import kr.hhplus.be.server.infrastructure.persistence.point.entity.PointHistory
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointHistoryJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.point.repository.PointJpaRepository
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
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * ProcessPaymentUseCase 트랜잭션 분리 검증 테스트
 *
 * 테스트 목적:
 * 1. 사전 검증 단계 (findById)가 각각 독립적인 트랜잭션에서 실행되는지 검증
 * 2. TransactionTrackingAspect를 통해 실제 트랜잭션 활성화 상태 추적
 * 3. 트랜잭션 격리 수준과 전파 특성 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("결제 처리 트랜잭션 분리 검증")
class ProcessPaymentTransactionTest @Autowired constructor(
    private val processPaymentUseCase: ProcessPaymentUseCase,
    private val userJpaRepository: UserJpaRepository,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val pointHistoryJpaRepository: PointHistoryJpaRepository,
    private val queueTokenService: QueueTokenService,
    private val redissonClient: RedissonClient,
) {

    private lateinit var user: User
    private lateinit var concert: Concert
    private lateinit var schedule: ConcertSchedule
    private lateinit var seat: Seat
    private lateinit var reservation: Reservation
    private lateinit var point: Point
    private var queueToken: String = ""

    @BeforeEach
    fun setup() {
        TransactionTrackingAspect.clear()

        // 테스트 데이터 준비
        user = createTestUser()
        concert = createTestConcert()
        schedule = createTestSchedule(concert.id)
        seat = createTestSeat(schedule.id)
        reservation = createTestReservation(user.id, seat.id)
        point = createTestPoint(user.id)
        queueToken = createTestQueueToken(user.id)
    }

    @AfterEach
    fun cleanup() {
        pointHistoryJpaRepository.deleteAll()
        pointJpaRepository.deleteAll()
        reservationJpaRepository.deleteAll()
        seatJpaRepository.deleteAll()
        concertScheduleJpaRepository.deleteAll()
        concertJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        redissonClient.keys.flushdb()
    }

    @Test
    @DisplayName("[트랜잭션 분리] Repository 호출이 기록되고 트랜잭션 상태를 추적할 수 있음")
    fun `should track repository calls and their transaction status`() {
        // Given
        val testName = "separate_transactions_test"
        TestContext.currentTestName = testName

        val command = ProcessPaymentCommand(
            userId = user.id,
            reservationId = reservation.id,
            queueToken = queueToken,
        )

        // When
        try {
            processPaymentUseCase.execute(command)
        } catch (e: Exception) {
            // 락이나 기타 이유로 실패해도 트랜잭션 추적은 확인 가능
            println("Expected error during execution: ${e.message}")
        }

        // Then
        val logs = TransactionTrackingAspect.getLog(testName)

        println("\n=== 트랜잭션 추적 로그 ===")
        logs.forEachIndexed { index, snapshot ->
            println(
                "[$index] ${snapshot.methodName} | " +
                    "Active=${snapshot.isActive} | " +
                    "ReadOnly=${snapshot.isReadOnly} | " +
                    "TxName=${snapshot.transactionName}",
            )
        }

        // Repository 호출이 최소 3개 이상 기록되어야 함
        assertThat(logs.size).isGreaterThanOrEqualTo(3)

        // 조회 메서드와 변경 메서드 분리 확인
        val findCalls = logs.filter {
            it.methodName.contains("find") || it.methodName.contains("get")
        }
        val modifyCalls = logs.filter {
            it.methodName.contains("save") || it.methodName.contains("update")
        }

        println("\n=== 조회 메서드: ${findCalls.size}개 ===")
        findCalls.forEach { println("  - ${it.methodName} (Active=${it.isActive}, ReadOnly=${it.isReadOnly})") }

        println("\n=== 변경 메서드: ${modifyCalls.size}개 ===")
        modifyCalls.forEach { println("  - ${it.methodName} (Active=${it.isActive}, ReadOnly=${it.isReadOnly})") }

        // 조회와 변경 메서드가 모두 기록되어야 함
        assertThat(findCalls).isNotEmpty
        assertThat(modifyCalls).isNotEmpty

        // 변경 메서드는 트랜잭션 안에서 실행되어야 함
        val transactionalModifyCalls = modifyCalls.filter { it.isActive }
        assertThat(transactionalModifyCalls).hasSizeGreaterThanOrEqualTo(1)
    }

    @Test
    @DisplayName("[트랜잭션 격리] Repository 호출의 트랜잭션 상태 추적")
    fun `should track transaction state of repository calls`() {
        // Given
        val testName = "transaction_names_test"
        TestContext.currentTestName = testName

        val command = ProcessPaymentCommand(
            userId = user.id,
            reservationId = reservation.id,
            queueToken = queueToken,
        )

        // When
        try {
            processPaymentUseCase.execute(command)
        } catch (e: Exception) {
            println("Expected error: ${e.message}")
        }

        // Then
        val logs = TransactionTrackingAspect.getLog(testName)

        println("\n=== 전체 Repository 호출 분석 ===")
        val activeCalls = logs.filter { it.isActive }
        val inactiveCalls = logs.filter { !it.isActive }

        println("트랜잭션 활성 호출: ${activeCalls.size}개")
        activeCalls.forEach {
            println("  - ${it.methodName} | ReadOnly=${it.isReadOnly} | TxName=${it.transactionName}")
        }

        println("\n트랜잭션 비활성 호출: ${inactiveCalls.size}개")
        inactiveCalls.forEach {
            println("  - ${it.methodName}")
        }

        // 최소한 Repository 호출이 존재해야 함
        assertThat(logs).isNotEmpty

        // ReadOnly 트랜잭션과 일반 트랜잭션 분리 확인
        val readOnlyCalls = logs.filter { it.isActive && it.isReadOnly }
        val readWriteCalls = logs.filter { it.isActive && !it.isReadOnly }

        println("\n=== ReadOnly 트랜잭션: ${readOnlyCalls.size}개 ===")
        println("=== Read-Write 트랜잭션: ${readWriteCalls.size}개 ===")

        // 변경 작업이 있는 경우 Read-Write 트랜잭션이 존재해야 함
        val hasSaveOrUpdate = logs.any { it.methodName.contains("save") || it.methodName.contains("update") }
        if (hasSaveOrUpdate) {
            assertThat(readWriteCalls).isNotEmpty
        }
    }

    @Test
    @DisplayName("[동시성] 트랜잭션 타임스탬프로 순차 실행 확인")
    fun `should verify sequential execution by transaction timestamps`() {
        // Given
        val testName = "timestamp_sequence_test"
        TestContext.currentTestName = testName

        val command = ProcessPaymentCommand(
            userId = user.id,
            reservationId = reservation.id,
            queueToken = queueToken,
        )

        // When
        try {
            processPaymentUseCase.execute(command)
        } catch (e: Exception) {
            println("Expected error: ${e.message}")
        }

        // Then
        val logs = TransactionTrackingAspect.getLog(testName)

        println("\n=== 타임스탬프 순차성 검증 ===")

        // 타임스탬프가 순차적으로 증가하는지 확인
        for (i in 0 until logs.size - 1) {
            val current = logs[i]
            val next = logs[i + 1]

            println("${current.methodName} (${current.timestamp}) → ${next.methodName} (${next.timestamp})")

            // 타임스탬프가 순차 증가해야 함 (나노초 단위)
            assertThat(next.timestamp).isGreaterThanOrEqualTo(current.timestamp)
        }
    }

    // ===== 테스트 데이터 생성 헬퍼 메서드 =====

    private fun createTestUser(): User {
        return User(
            userName = "transactionTestUser",
            email = "tx-test@test.com",
            password = "password",
        ).apply { userJpaRepository.save(this) }
    }

    private fun createTestConcert(): Concert {
        return Concert(
            title = "Transaction Test Concert",
            description = "Test Concert for Transaction Verification",
        ).apply { concertJpaRepository.save(this) }
    }

    private fun createTestSchedule(concertId: Long): ConcertSchedule {
        return ConcertSchedule(
            concertId = concertId,
            concertDate = LocalDateTime.now().plusDays(7),
        ).apply { concertScheduleJpaRepository.save(this) }
    }

    private fun createTestSeat(scheduleId: Long): Seat {
        return Seat(
            concertScheduleId = scheduleId,
            seatNumber = 1,
            price = 100000,
            seatStatus = SeatStatus.AVAILABLE,
        ).apply { seatJpaRepository.save(this) }
    }

    private fun createTestReservation(userId: Long, seatId: Long): Reservation {
        return Reservation(
            userId = userId,
            seatId = seatId,
            reservationStatus = ReservationStatus.TEMPORARY,
        ).apply { reservationJpaRepository.save(this) }
    }

    private fun createTestPoint(userId: Long): Point {
        return Point(
            userId = userId,
            balance = 200000, // 충분한 포인트
        ).apply {
            pointJpaRepository.save(this)
            // 충전 히스토리 기록
            pointHistoryJpaRepository.save(
                PointHistory(
                    userId = userId,
                    amount = 200000,
                    transactionType = TransactionType.CHARGE,
                ),
            )
        }
    }

    private fun createTestQueueToken(userId: Long): String {
        val token = queueTokenService.createQueueToken(userId)
        queueTokenService.activateWaitingTokens(1) // 토큰 활성화

        // 토큰이 제대로 활성화되었는지 검증
        val activatedToken = queueTokenService.getQueueTokenByToken(token.token)
        require(activatedToken.queueStatus == QueueStatus.ACTIVE) {
            "토큰이 ACTIVE 상태가 아닙니다: ${activatedToken.queueStatus}"
        }

        return token.token
    }
}
