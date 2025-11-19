package kr.hhplus.be.server.infrastructure.persistence.reservation.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.Seat
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import java.time.LocalDateTime

@Entity
@Table(name = "reservation")
class Reservation(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    val seat: Seat,

    @Enumerated(EnumType.STRING)
    var reservationStatus: ReservationStatus = ReservationStatus.TEMPORARY,
) : BaseEntity() {

    var temporaryReservedAt: LocalDateTime = LocalDateTime.now()
    var temporaryExpiredAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)

    fun toModel(): ReservationModel {
        return ReservationModel.reconstitute(
            id = id,
            userId = user.id,
            seatId = seat.id,
            reservationStatus = reservationStatus,
            temporaryReservedAt = temporaryReservedAt,
            temporaryExpiredAt = temporaryExpiredAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(reservationModel: ReservationModel) {
        this.reservationStatus = reservationModel.reservationStatus
        this.temporaryReservedAt = reservationModel.temporaryReservedAt
        this.temporaryExpiredAt = reservationModel.temporaryExpiredAt
    }

    companion object {
        fun fromDomain(
            reservationModel: ReservationModel,
            user: User,
            seat: Seat,
        ): Reservation {
            return Reservation(
                user = user,
                seat = seat,
            )
        }
    }
}
