package kr.hhplus.be.server.infrastructure.persistence.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "seat")
class Seat(
    @ManyToOne
    @JoinColumn(name = "concert_schedule_id", nullable = false)
    val concertSchedule: ConcertSchedule,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    var seatStatus: SeatStatus,

    var price: Int,
) : BaseEntity() {

    fun toModel(): SeatModel {
        return SeatModel.reconstitute(
            id = id,
            concertScheduleId = concertSchedule.id,
            seatNumber = seatNumber,
            seatStatus = seatStatus,
            price = price,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(seatModel: SeatModel) {
        this.seatStatus = seatModel.seatStatus
        this.price = seatModel.price
    }

    companion object {
        fun fromDomain(
            seatModel: SeatModel,
            concertSchedule: ConcertSchedule,
        ): Seat {
            return Seat(
                concertSchedule = concertSchedule,
                seatNumber = seatModel.seatNumber,
                seatStatus = seatModel.seatStatus,
                price = seatModel.price,
            )
        }
    }
}
