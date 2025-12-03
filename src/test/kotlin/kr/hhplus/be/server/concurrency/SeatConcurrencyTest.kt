package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 좌석 Lock 동시성 테스트
 *
 * 목적: Pessimistic Lock이 제대로 동작하는지 검증
 * Rule: 동일 좌석에 대한 Lock은 순차적으로만 획득 가능
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("좌석 Lock 동시성 테스트")
class SeatConcurrencyTest {

    @Autowired
    private lateinit var seatJpaRepository: SeatJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @AfterEach
    fun cleanup() {
        seatJpaRepository.deleteAll()
    }

    @Test
    @DisplayName("동일 좌석 10개 스레드 동시 Lock 시도 - 첫 번째만 상태 변경 성공")
    fun `should acquire lock sequentially for same seat`() {
        // Given: 예약 가능한 좌석 1개 저장
        val seat = Seat(
            concertScheduleId = 1L,
            seatNumber = 1,
            seatStatus = SeatStatus.AVAILABLE,
            price = 100000,
        )
        val saved = seatJpaRepository.save(seat)

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val alreadyReservedCount = AtomicInteger(0)

        // When: 10개 스레드가 동시에 Lock 시도
        repeat(threadCount) {
            executor.submit {
                try {
                    transactionTemplate.execute {
                        // Pessimistic Lock 획득
                        val lockedSeat = seatJpaRepository.findByIdWithLock(saved.id)

                        // 상태 확인 및 변경
                        if (lockedSeat != null && lockedSeat.seatStatus == SeatStatus.AVAILABLE) {
                            lockedSeat.seatStatus = SeatStatus.TEMPORARY_RESERVED
                            seatJpaRepository.save(lockedSeat)
                            successCount.incrementAndGet()
                            Thread.sleep(50) // Lock 보유 시뮬레이션
                        } else {
                            alreadyReservedCount.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 1개만 성공, 9개는 이미 예약됨 확인
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(alreadyReservedCount.get()).isEqualTo(threadCount - 1)

        // 최종 상태 확인
        val result = seatJpaRepository.findById(saved.id).orElse(null)
        assertThat(result.seatStatus).isEqualTo(SeatStatus.TEMPORARY_RESERVED)
    }

    @Test
    @DisplayName("서로 다른 좌석 5개 동시 Lock - 모두 독립적으로 성공")
    fun `should acquire locks independently for different seats`() {
        // Given: 5개의 서로 다른 좌석
        val seats = (1..5).map { seatNumber ->
            seatJpaRepository.save(
                Seat(
                    concertScheduleId = 1L,
                    seatNumber = seatNumber,
                    seatStatus = SeatStatus.AVAILABLE,
                    price = 100000,
                ),
            )
        }

        val executor = Executors.newFixedThreadPool(5)
        val latch = CountDownLatch(5)
        val successCount = AtomicInteger(0)

        // When: 각 좌석에 대해 동시 Lock
        seats.forEach { seat ->
            executor.submit {
                try {
                    transactionTemplate.execute {
                        val locked = seatJpaRepository.findByIdWithLock(seat.id)
                        if (locked != null) {
                            locked.seatStatus = SeatStatus.TEMPORARY_RESERVED
                            seatJpaRepository.save(locked)
                            successCount.incrementAndGet()
                            Thread.sleep(50)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 5개 모두 성공
        assertThat(successCount.get()).isEqualTo(5)

        seats.forEach { seat ->
            val result = seatJpaRepository.findById(seat.id).orElse(null)
            assertThat(result.seatStatus).isEqualTo(SeatStatus.TEMPORARY_RESERVED)
        }
    }
}
