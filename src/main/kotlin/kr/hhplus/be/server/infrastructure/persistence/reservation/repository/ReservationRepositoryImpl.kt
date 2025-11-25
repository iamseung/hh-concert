package kr.hhplus.be.server.infrastructure.persistence.reservation.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.model.ReservationStatus
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.repository.SeatJpaRepository
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import kr.hhplus.be.server.infrastructure.persistence.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val seatJpaRepository: SeatJpaRepository,
) : ReservationRepository {

    override fun save(reservationModel: ReservationModel): ReservationModel {
        val entity = if (reservationModel.id != 0L) {
            reservationJpaRepository.findByIdOrNull(reservationModel.id)?.apply {
                updateFromDomain(reservationModel)
            } ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
        } else {
            val user = userJpaRepository.findByIdOrNull(reservationModel.userId)
                ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            val seat = seatJpaRepository.findByIdOrNull(reservationModel.seatId)
                ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
            Reservation.fromDomain(reservationModel, user, seat)
        }
        val saved = reservationJpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): ReservationModel? {
        return reservationJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): ReservationModel {
        return findById(id) ?: throw BusinessException(ErrorCode.RESERVATION_NOT_FOUND)
    }

    override fun findAllByUserId(userId: Long): List<ReservationModel> {
        return reservationJpaRepository.findAllByUserId(userId).map { it.toModel() }
    }
}
