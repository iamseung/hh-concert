package kr.hhplus.be.server.application.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.infrastructure.cache.SeatCacheService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SeatSchedulerTest {

    private val seatService: SeatService = mockk()
    private val reservationService: ReservationService = mockk()
    private val seatCacheService: SeatCacheService = mockk(relaxed = true)
    private val seatScheduler = SeatScheduler(seatService, reservationService, seatCacheService)

    @Test
    @DisplayName("만료된 임시 좌석을 복원한다")
    fun restoreExpiredTemporarySeats() {
        // given
        val expiredSeatIds = listOf(1L, 2L, 3L)
        val scheduleId = 100L
        val now = LocalDateTime.now()
        val expiredSeats = expiredSeatIds.map { id ->
            SeatModel.reconstitute(
                id = id,
                concertScheduleId = scheduleId,
                seatNumber = id.toInt(),
                seatStatus = SeatStatus.TEMPORARY_RESERVED,
                price = 10000,
                createdAt = now,
                updatedAt = now,
            )
        }
        val expectedRestoredCount = 3

        every { reservationService.findExpiredReservationSeatIds(any()) } returns expiredSeatIds
        every { seatService.findAllById(expiredSeatIds) } returns expiredSeats
        every { seatService.restoreExpiredSeats(expiredSeatIds) } returns expectedRestoredCount

        // when
        seatScheduler.restoreExpiredTemporarySeats()

        // then
        verify(exactly = 1) { seatService.findAllById(expiredSeatIds) }
        verify(exactly = 1) { seatService.restoreExpiredSeats(expiredSeatIds) }
        verify(exactly = 1) { seatCacheService.evictAvailableSeats(scheduleId) }
    }

    @Test
    @DisplayName("복원할 임시 좌석이 없으면 조기 종료한다")
    fun restoreExpiredTemporarySeats_noExpiredSeats() {
        // given
        every { reservationService.findExpiredReservationSeatIds(any()) } returns emptyList()

        // when
        seatScheduler.restoreExpiredTemporarySeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
        verify(exactly = 0) { seatService.findAllById(any()) }
        verify(exactly = 0) { seatService.restoreExpiredSeats(any()) }
    }

    @Test
    @DisplayName("스케줄러 실행 중 에러가 발생해도 다음 실행에 영향을 주지 않는다")
    fun restoreExpiredTemporarySeats_errorHandling() {
        // given
        every { reservationService.findExpiredReservationSeatIds(any()) } throws RuntimeException("Test exception")

        // when
        seatScheduler.restoreExpiredTemporarySeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
    }
}
