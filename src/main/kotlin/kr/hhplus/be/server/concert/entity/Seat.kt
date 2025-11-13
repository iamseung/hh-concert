package kr.hhplus.be.server.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import kr.hhplus.be.server.common.BaseEntity

@Entity
class Seat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "concert_scheduled_id", nullable = false)
    val concertScheduled: ConcertScheduled,

    val seatNumber: Int,

    @Enumerated(EnumType.STRING)
    val seatStatus: SeatStatus,

    val price: Int,
) : BaseEntity() {
}