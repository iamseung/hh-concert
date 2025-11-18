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
        val entity = if (reservation.id != 0L) {
            reservationJpaRepository.findByIdOrNull(reservation.id)?.apply {
                updateFromDomain(reservation)
            } ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(reservation.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val seat = seatJpaRepository.findByIdOrNull(reservation.seatId)
                ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
            ReservationEntity.fromDomain(reservation, user, seat)
        }
        val saved = reservationJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): Reservation? {
        return reservationJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): Reservation {
        return findById(id) ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
    }

    override fun findByUserIdAndSeatId(userId: Long, seatId: Long): Reservation? {
        return reservationJpaRepository.findByUserIdAndSeatId(userId, seatId)?.toDomain()
    }

    override fun findAllByUserId(userId: Long): List<Reservation> {
        return reservationJpaRepository.findAllByUserId(userId).map { it.toDomain() }
    }

    override fun findAllByStatus(status: ReservationStatus): List<Reservation> {
        return reservationJpaRepository.findAllByReservationStatus(status).map { it.toDomain() }
    }

    override fun findExpiredReservations(): List<Reservation> {
        return reservationJpaRepository.findExpiredReservations(LocalDateTime.now()).map { it.toDomain() }
    }
}
