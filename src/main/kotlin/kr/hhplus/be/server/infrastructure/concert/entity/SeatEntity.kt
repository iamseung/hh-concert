package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.SeatStatus

@Entity
@Table(name = "seat")
class SeatEntity(
    @ManyToOne
    @JoinColumn(name = "concert_schedule_id", nullable = false)
    val concertScheduleEntity: ConcertScheduleEntity,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    var seatStatus: SeatStatus,

    val price: Int,
) : BaseEntity() {

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
    }

    fun confirmReservation() {
        if (seatStatus != SeatStatus.TEMPORARY_RESERVED) {
            throw BusinessException(ErrorCode.SEAT_NOT_AVAILABLE)
        }
        this.seatStatus = SeatStatus.RESERVED
    }
}
