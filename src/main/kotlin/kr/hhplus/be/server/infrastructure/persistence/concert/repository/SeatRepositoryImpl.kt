package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.SeatModel
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.repository.SeatRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SeatRepositoryImpl(
    private val seatJpaRepository: SeatJpaRepository,
) : SeatRepository {

    override fun findById(id: Long): SeatModel? {
        return seatJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): SeatModel {
        return findById(id) ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
    }

    override fun findByIdWithLock(id: Long): SeatModel {
        return seatJpaRepository.findByIdWithLock(id)?.toModel()
            ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
    }

    override fun findAllByConcertScheduleId(concertScheduleId: Long): List<SeatModel> {
        return seatJpaRepository.findAllByConcertScheduleId(concertScheduleId).map { it.toModel() }
    }

    override fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<SeatModel> {
        return seatJpaRepository.findAllByConcertScheduleIdAndSeatStatus(concertScheduleId, status).map { it.toModel() }
    }
}
