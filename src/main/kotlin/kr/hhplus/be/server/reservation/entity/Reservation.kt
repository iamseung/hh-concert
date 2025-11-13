package kr.hhplus.be.server.reservation.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import kr.hhplus.be.server.common.BaseEntity
import kr.hhplus.be.server.concert.entity.Seat
import kr.hhplus.be.server.user.User
import java.time.LocalDateTime

@Entity
class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    val seat: Seat,

    @Enumerated(EnumType.STRING)
    val reservationStatus: ReservationStatus,
) : BaseEntity() {

    val temporaryReservedAt: LocalDateTime = LocalDateTime.now()
    val temporaryExpiredAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)
}