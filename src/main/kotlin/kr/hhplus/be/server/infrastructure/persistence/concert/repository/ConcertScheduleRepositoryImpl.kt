package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.repository.ConcertScheduleRepository
import kr.hhplus.be.server.infrastructure.persistence.concert.entity.ConcertSchedule
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertScheduleRepositoryImpl(
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
) : ConcertScheduleRepository {

    override fun save(concertScheduleModel: ConcertScheduleModel): ConcertScheduleModel {
        val entity = if (concertScheduleModel.id != 0L) {
            concertScheduleJpaRepository.findByIdOrNull(concertScheduleModel.id)?.apply {
                updateFromDomain(concertScheduleModel)
            } ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
        } else {
            val concert = concertJpaRepository.findByIdOrNull(concertScheduleModel.concertId)
                ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
            ConcertSchedule.fromDomain(concertScheduleModel, concert)
        }
        val saved = concertScheduleJpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): ConcertScheduleModel? {
        return concertScheduleJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): ConcertScheduleModel {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
    }

    override fun findAllByConcertId(concertId: Long): List<ConcertScheduleModel> {
        return concertScheduleJpaRepository.findByConcertId(concertId).map { it.toModel() }
    }

    override fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertScheduleModel> {
        return concertScheduleJpaRepository.findAvailableSchedules(concertId, fromDate).map { it.toModel() }
    }
}
