package kr.hhplus.be.server.infrastructure.reservation.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.concert.entity.SeatEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import java.time.LocalDateTime

@Entity
@Table(name = "reservation")
class ReservationEntity(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val userEntity: UserEntity,

    @ManyToOne
    @JoinColumn(name = "seat_id", nullable = false)
    val seatEntity: SeatEntity,

    @Enumerated(EnumType.STRING)
    var reservationStatus: ReservationStatus = ReservationStatus.TEMPORARY,
) : BaseEntity() {

    var temporaryReservedAt: LocalDateTime = LocalDateTime.now()
    var temporaryExpiredAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)

    fun toDomain(): Reservation {
        return Reservation.reconstitute(
            id = id,
            userId = userEntity.id,
            seatId = seatEntity.id,
            reservationStatus = reservationStatus,
            temporaryReservedAt = temporaryReservedAt,
            temporaryExpiredAt = temporaryExpiredAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(reservation: Reservation) {
        this.reservationStatus = reservation.reservationStatus
        this.temporaryReservedAt = reservation.temporaryReservedAt
        this.temporaryExpiredAt = reservation.temporaryExpiredAt
    }

    companion object {
        fun fromDomain(
            reservation: Reservation,
            userEntity: UserEntity,
            seatEntity: SeatEntity,
        ): ReservationEntity {
            return ReservationEntity(
                userEntity = userEntity,
                seatEntity = seatEntity,
            )
        }
    }
}
