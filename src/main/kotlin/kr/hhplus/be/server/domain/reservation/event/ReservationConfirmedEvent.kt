package kr.hhplus.be.server.domain.reservation.event

/**
 * 예약 확정 이벤트
 *
 * 결제가 완료되어 예약이 확정되었을 때 발행됩니다.
 *
 * @property reservationId 예약 ID
 * @property concertId 콘서트 ID
 * @property concertTitle 콘서트 제목
 * @property userId 사용자 ID
 */
data class ReservationConfirmedEvent(
    val reservationId: Long,
    val concertId: Long,
    val concertTitle: String,
    val userId: Long,
)
