package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.repository.ConcertScheduleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertScheduleRepositoryImpl(
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
) : ConcertScheduleRepository {

    override fun findById(id: Long): ConcertScheduleModel? {
        return concertScheduleJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): ConcertScheduleModel {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
    }

    override fun findAllByConcertId(concertId: Long): List<ConcertScheduleModel> {
        return concertScheduleJpaRepository.findByConcertId(concertId).map { it.toModel() }
    }
}
