package kr.hhplus.be.server.application.usecase.reservation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.service.ConcertScheduleService
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.queue.model.QueueStatus
import kr.hhplus.be.server.domain.queue.model.QueueTokenModel
import kr.hhplus.be.server.domain.queue.service.QueueTokenService
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import kr.hhplus.be.server.infrastructure.lock.DistributeLockExecutor
import kr.hhplus.be.server.infrastructure.template.TransactionExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CreateReservationUseCaseTest {

    private lateinit var createReservationUseCase: CreateReservationUseCase
    private lateinit var userService: UserService
    private lateinit var seatService: SeatService
    private lateinit var reservationService: ReservationService
    private lateinit var queueTokenService: QueueTokenService
    private lateinit var concertScheduleService: ConcertScheduleService
    private lateinit var distributeLockExecutor: DistributeLockExecutor
    private lateinit var transactionExecutor: TransactionExecutor
    private lateinit var seatCacheService: SeatCacheService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        seatService = mockk()
        reservationService = mockk()
        queueTokenService = mockk()
        concertScheduleService = mockk()
        distributeLockExecutor = mockk()
        transactionExecutor = mockk()
        seatCacheService = mockk(relaxed = true)

        // Mock the lock executor to just execute the logic
        every {
            distributeLockExecutor.execute<Any>(any(), any(), any(), any())
        } answers {
            val logic = arg<() -> Any>(3)
            logic()
        }

        // Mock the transaction executor to just execute the logic
        every {
            transactionExecutor.execute<Any>(any())
        } answers {
            val action = arg<() -> Any>(0)
            action()
        }

        createReservationUseCase = CreateReservationUseCase(
            userService = userService,
            seatService = seatService,
            reservationService = reservationService,
            queueTokenService = queueTokenService,
            concertScheduleService = concertScheduleService,
            distributeLockExecutor = distributeLockExecutor,
            transactionExecutor = transactionExecutor,
            seatCacheService = seatCacheService,
        )
    }

    @Test
    @DisplayName("좌석 예약 생성 성공")
    fun execute_Success() {
        // given
        val userId = 1L
        val scheduleId = 1L
        val seatId = 1L
        val queueToken = "test-token"
        val command = CreateReservationCommand(
            userId = userId,
            scheduleId = scheduleId,
            seatId = seatId,
            queueToken = queueToken,
        )

        val user = UserModel.reconstitute(
            id = userId,
            userName = "testUser",
            email = "test@test.com",
            password = "password",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val schedule = ConcertScheduleModel.reconstitute(
            id = scheduleId,
            concertId = 1L,
            concertDate = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val seat = SeatModel.reconstitute(
            id = seatId,
            concertScheduleId = scheduleId,
            seatNumber = 1,
            price = 100000,
            seatStatus = SeatStatus.AVAILABLE,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val reservation = ReservationModel.reconstitute(
            id = 1L,
            userId = userId,
            seatId = seatId,
            reservationStatus = ReservationStatus.TEMPORARY,
            temporaryReservedAt = LocalDateTime.now(),
            temporaryExpiredAt = LocalDateTime.now().plusMinutes(5),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val token = QueueTokenModel.reconstitute(
            userId = userId,
            token = queueToken,
            queueStatus = QueueStatus.ACTIVE,
            activatedAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusMinutes(10),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        every { queueTokenService.getQueueTokenByToken(queueToken) } returns token
        every { queueTokenService.expireQueueToken(any()) } returns token
        every { userService.findById(userId) } returns user
        every { concertScheduleService.findById(scheduleId) } returns schedule
        every { seatService.findByIdAndConcertScheduleId(seatId, scheduleId) } returns seat
        every { seatService.update(any()) } returns seat
        every { reservationService.save(any()) } returns reservation

        // when
        val result = createReservationUseCase.execute(command)

        // then
        assertThat(result.reservationId).isEqualTo(reservation.id)
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.seatId).isEqualTo(seatId)
        verify(exactly = 1) { reservationService.save(any()) }
    }
}
