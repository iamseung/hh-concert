package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "seat")
class SeatEntity(
    @ManyToOne
    @JoinColumn(name = "concert_schedule_id", nullable = false)
    val concertScheduleEntity: ConcertScheduleEntity,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    var seatStatus: SeatStatus,

    var price: Int,
) : BaseEntity() {

    fun toDomain(): Seat {
        return Seat.reconstitute(
            id = id,
            concertScheduleId = concertScheduleEntity.id,
            seatNumber = seatNumber,
            seatStatus = seatStatus,
            price = price,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(seat: Seat) {
        this.seatStatus = seat.seatStatus
        this.price = seat.price
    }

    companion object {
        fun fromDomain(
            seat: Seat,
            concertScheduleEntity: ConcertScheduleEntity,
        ): SeatEntity {
            return SeatEntity(
                concertScheduleEntity = concertScheduleEntity,
                seatNumber = seat.seatNumber,
                seatStatus = seat.seatStatus,
                price = seat.price,
            )
        }
    }
}
