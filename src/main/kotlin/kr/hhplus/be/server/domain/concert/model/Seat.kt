package kr.hhplus.be.server.domain.concert.model

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDateTime

class Seat private constructor(
    var id: Long,
    val concertScheduleId: Long,
    val seatNumber: Int,
    var seatStatus: SeatStatus,
    val price: Int,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    val isAvailable: Boolean
        get() = seatStatus == SeatStatus.AVAILABLE

    fun validateAvailable() {
        if (!isAvailable) {
            throw BusinessException(ErrorCode.SEAT_NOT_AVAILABLE)
        }
    }

    fun temporaryReservation() {
        validateAvailable()
        this.seatStatus = SeatStatus.TEMPORARY_RESERVED
        this.updatedAt = LocalDateTime.now()
    }

    fun confirmReservation() {
        if (seatStatus != SeatStatus.TEMPORARY_RESERVED) {
            throw BusinessException(ErrorCode.SEAT_NOT_AVAILABLE)
        }
        this.seatStatus = SeatStatus.RESERVED
        this.updatedAt = LocalDateTime.now()
    }

    companion object {
        fun create(concertScheduleId: Long, seatNumber: Int, price: Int): Seat {
            val now = LocalDateTime.now()
            return Seat(
                id = 0L,
                concertScheduleId = concertScheduleId,
                seatNumber = seatNumber,
                seatStatus = SeatStatus.AVAILABLE,
                price = price,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            concertScheduleId: Long,
            seatNumber: Int,
            seatStatus: SeatStatus,
            price: Int,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): Seat {
            return Seat(
                id = id,
                concertScheduleId = concertScheduleId,
                seatNumber = seatNumber,
                seatStatus = seatStatus,
                price = price,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
