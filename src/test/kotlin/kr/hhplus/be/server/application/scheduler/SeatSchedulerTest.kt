package kr.hhplus.be.server.application.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.concert.service.SeatService
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SeatSchedulerTest {

    private val seatService: SeatService = mockk()
    private val reservationService: ReservationService = mockk()
    private val seatScheduler = SeatScheduler(seatService, reservationService)

    @Test
    @DisplayName("만료된 임시 좌석을 복원한다")
    fun restoreExpiredTemporarySeats() {
        // given
        val expiredSeatIds = listOf(1L, 2L, 3L)
        val expectedRestoredCount = 3
        every { reservationService.findExpiredReservationSeatIds(any()) } returns expiredSeatIds
        every { seatService.restoreExpiredSeats(expiredSeatIds) } returns expectedRestoredCount

        // when
        seatScheduler.restoreExpiredTemporarySeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
        verify(exactly = 1) { seatService.restoreExpiredSeats(expiredSeatIds) }
    }

    @Test
    @DisplayName("복원할 임시 좌석이 없어도 에러가 발생하지 않는다")
    fun restoreExpiredTemporarySeats_noExpiredSeats() {
        // given
        val emptyList = emptyList<Long>()
        every { reservationService.findExpiredReservationSeatIds(any()) } returns emptyList
        every { seatService.restoreExpiredSeats(emptyList) } returns 0

        // when
        seatScheduler.restoreExpiredTemporarySeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
        verify(exactly = 1) { seatService.restoreExpiredSeats(emptyList) }
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
