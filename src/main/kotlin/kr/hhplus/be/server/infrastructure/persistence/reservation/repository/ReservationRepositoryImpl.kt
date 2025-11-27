package kr.hhplus.be.server.infrastructure.persistence.reservation.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.reservation.model.ReservationModel
import kr.hhplus.be.server.domain.reservation.repository.ReservationRepository
import kr.hhplus.be.server.infrastructure.persistence.reservation.entity.Reservation
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository,
) : ReservationRepository {

    override fun save(reservationModel: ReservationModel): ReservationModel {
        val reservation = Reservation.fromDomain(reservationModel)
        val saved = reservationJpaRepository.save(reservation)
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
