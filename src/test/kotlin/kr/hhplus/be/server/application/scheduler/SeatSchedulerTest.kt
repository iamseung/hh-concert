package kr.hhplus.be.server.application.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.reservation.service.ReservationService
import kr.hhplus.be.server.domain.seat.event.SeatExpiredEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class SeatSchedulerTest {

    private val reservationService: ReservationService = mockk()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mockk(relaxed = true)
    private val seatExpiredTopic = "seats.expired"
    private val seatScheduler = SeatScheduler(reservationService, kafkaTemplate, seatExpiredTopic)

    @Test
    @DisplayName("만료된 임시 좌석을 Kafka로 발행한다")
    fun publishExpiredSeats() {
        // given
        val expiredSeatIds = listOf(1L, 2L, 3L)
        val now = LocalDateTime.now()
        val future = CompletableFuture<SendResult<String, Any>>()

        every { reservationService.findExpiredReservationSeatIds(any()) } returns expiredSeatIds
        every { kafkaTemplate.send(seatExpiredTopic, any<SeatExpiredEvent>()) } returns future

        // when
        seatScheduler.publishExpiredSeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
        verify(exactly = 1) { kafkaTemplate.send(seatExpiredTopic, any<SeatExpiredEvent>()) }
    }

    @Test
    @DisplayName("복원할 임시 좌석이 없으면 Kafka 발행을 하지 않는다")
    fun publishExpiredSeats_noExpiredSeats() {
        // given
        every { reservationService.findExpiredReservationSeatIds(any()) } returns emptyList()

        // when
        seatScheduler.publishExpiredSeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any()) }
    }

    @Test
    @DisplayName("스케줄러 실행 중 에러가 발생해도 다음 실행에 영향을 주지 않는다")
    fun publishExpiredSeats_errorHandling() {
        // given
        every { reservationService.findExpiredReservationSeatIds(any()) } throws RuntimeException("Test exception")

        // when
        seatScheduler.publishExpiredSeats()

        // then
        verify(exactly = 1) { reservationService.findExpiredReservationSeatIds(any()) }
    }
}
