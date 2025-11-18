package kr.hhplus.be.server.domain.reservation.model

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDateTime

class Reservation private constructor(
    var id: Long,
    val userId: Long,
    val seatId: Long,
    var reservationStatus: ReservationStatus,
    val temporaryReservedAt: LocalDateTime,
    val temporaryExpiredAt: LocalDateTime,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun validateOwnership(userId: Long) {
        if (this.userId != userId) {
            throw BusinessException(ErrorCode.INVALID_RESERVATION)
        }
    }

    fun validatePayable() {
        if (reservationStatus != ReservationStatus.TEMPORARY) {
            throw BusinessException(ErrorCode.INVALID_RESERVATION_STATUS)
        }
    }

    fun confirmPayment() {
        validatePayable()
        this.reservationStatus = ReservationStatus.CONFIRMED
        this.updatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(userId: Long, seatId: Long): Reservation {
            val now = LocalDateTime.now()
            return Reservation(
                id = 0L,
                userId = userId,
                seatId = seatId,
                reservationStatus = ReservationStatus.TEMPORARY,
                temporaryReservedAt = now,
                temporaryExpiredAt = now.plusMinutes(5),
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            userId: Long,
            seatId: Long,
            reservationStatus: ReservationStatus,
            temporaryReservedAt: LocalDateTime,
            temporaryExpiredAt: LocalDateTime,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): Reservation {
            return Reservation(
                id = id,
                userId = userId,
                seatId = seatId,
                reservationStatus = reservationStatus,
                temporaryReservedAt = temporaryReservedAt,
                temporaryExpiredAt = temporaryExpiredAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
