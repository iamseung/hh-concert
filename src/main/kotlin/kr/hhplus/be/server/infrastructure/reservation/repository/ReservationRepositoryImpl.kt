package kr.hhplus.be.server.infrastructure.reservation.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.reservation.model.Reservation
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.infrastructure.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.reservation.entity.ReservationEntity
import kr.hhplus.be.server.infrastructure.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
) : ReservationRepository {

    override fun save(reservation: Reservation): Reservation {
        val entity = toEntity(reservation)
        val saved = reservationJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Reservation? {
        return reservationJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): Reservation {
        return findById(id) ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
    }

    override fun findByUserIdAndSeatId(userId: Long, seatId: Long): Reservation? {
        return reservationJpaRepository.findByUserIdAndSeatId(userId, seatId)?.let { toDomain(it) }
    }

    override fun findAllByUserId(userId: Long): List<Reservation> {
        return reservationJpaRepository.findAllByUserId(userId).map { toDomain(it) }
    }

    override fun findAllByStatus(status: ReservationStatus): List<Reservation> {
        return reservationJpaRepository.findAllByReservationStatus(status).map { toDomain(it) }
    }

    override fun findExpiredReservations(): List<Reservation> {
        return reservationJpaRepository.findExpiredReservations(LocalDateTime.now()).map { toDomain(it) }
    }

    private fun toDomain(reservation: ReservationEntity): Reservation {
        return Reservation.reconstitute(
            id = reservation.id,
            userId = reservation.userEntity.id,
            seatId = reservation.seatEntity.id,
            reservationStatus = reservation.reservationStatus,
            temporaryReservedAt = reservation.temporaryReservedAt,
            temporaryExpiredAt = reservation.temporaryExpiredAt,
            createdAt = reservation.createdAt,
            updatedAt = reservation.updatedAt,
        )
    }

    private fun toEntity(reservation: Reservation): ReservationEntity {
        val user = userJpaRepository.findByIdOrNull(reservation.userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val seat = seatJpaRepository.findByIdOrNull(reservation.seatId)
            ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)

        val entity = ReservationEntity.of(user, seat)
        entity.reservationStatus = reservation.reservationStatus
        return entity
    }
}