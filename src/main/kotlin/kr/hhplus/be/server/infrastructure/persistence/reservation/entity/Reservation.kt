package kr.hhplus.be.server.infrastructure.persistence.reservation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(name = "reservation")
class Reservation(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "seat_id", nullable = false)
    val seatId: Long,

    @Enumerated(EnumType.STRING)
    var reservationStatus: ReservationStatus = ReservationStatus.TEMPORARY,
) : BaseEntity() {

    var temporaryReservedAt: LocalDateTime = LocalDateTime.now()
    var temporaryExpiredAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)

    fun toModel(): ReservationModel {
        return ReservationModel.reconstitute(
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

    fun updateFromDomain(reservationModel: ReservationModel) {
        this.reservationStatus = reservationModel.reservationStatus
        this.temporaryReservedAt = reservationModel.temporaryReservedAt
        this.temporaryExpiredAt = reservationModel.temporaryExpiredAt
    }

    companion object {
        fun fromDomain(reservationModel: ReservationModel): Reservation {
            return Reservation(
                userId = reservationModel.userId,
                seatId = reservationModel.seatId,
            )
        }
    }
}
