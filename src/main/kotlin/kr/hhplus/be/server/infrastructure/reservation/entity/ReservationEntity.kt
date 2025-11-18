package kr.hhplus.be.server.infrastructure.reservation.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
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

    val temporaryReservedAt: LocalDateTime = LocalDateTime.now()
    val temporaryExpiredAt: LocalDateTime = LocalDateTime.now().plusMinutes(5)

    fun validateOwnership(userId: Long) {
        if (this.userEntity.id != userId) {
            throw BusinessException(ErrorCode.INVALID_RESERVATION)
        }
    }

    fun validatePayable() {
        if (reservationStatus != ReservationStatus.TEMPORARY) {
            throw BusinessException(ErrorCode.INVALID_RESERVATION_STATUS)
        }
    }

    fun confirmPayment() {
        validatePayable()
        this.reservationStatus = ReservationStatus.CONFIRMED
    }

    companion object {
        fun of(userEntity: UserEntity, seatEntity: SeatEntity): ReservationEntity {
            return ReservationEntity(
                userEntity = userEntity,
                seatEntity = seatEntity,
            )
        }
    }
}