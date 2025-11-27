package kr.hhplus.be.server.infrastructure.persistence.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "seat")
class Seat(
    @Column(name = "concert_schedule_id", nullable = false)
    val concertScheduleId: Long,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    var seatStatus: SeatStatus,

    var price: Int,
) : BaseEntity() {

    fun toModel(): SeatModel {
        return SeatModel.reconstitute(
            id = id,
            concertScheduleId = concertScheduleId,
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
        fun fromDomain(seatModel: SeatModel): Seat {
            return Seat(
                concertScheduleId = seatModel.concertScheduleId,
                seatNumber = seatModel.seatNumber,
                seatStatus = seatModel.seatStatus,
                price = seatModel.price,
            )
        }
    }
}
