package kr.hhplus.be.server.domain.seat.event

import java.time.Instant

/**
 * 좌석 만료 이벤트
 *
 * 발행 시점: SeatScheduler가 만료된 좌석을 발견했을 때
 * 발행 위치: SeatScheduler
 * 소비자: SeatExpirationConsumer
 *
 * 용도:
 * - 만료된 좌석을 비동기로 복원 처리
 * - 스케줄러의 블로킹 제거 및 DB 부하 분산
 */
data class SeatExpiredEvent(
    val seatIds: List<Long>,
    val scheduleId: Long,
    val expiredAt: Instant,
)
