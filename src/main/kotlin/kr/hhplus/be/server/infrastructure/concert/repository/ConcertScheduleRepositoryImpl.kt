package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.ConcertSchedule
import kr.hhplus.be.server.domain.concert.repository.ConcertScheduleRepository
import kr.hhplus.be.server.infrastructure.concert.entity.ConcertScheduleEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertScheduleRepositoryImpl(
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
    private val concertJpaRepository: ConcertJpaRepository,
) : ConcertScheduleRepository {

    override fun save(concertSchedule: ConcertSchedule): ConcertSchedule {
        val entity = if (concertSchedule.id != 0L) {
            concertScheduleJpaRepository.findByIdOrNull(concertSchedule.id)?.apply {
                updateFromDomain(concertSchedule)
            } ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
        } else {
            val concert = concertJpaRepository.findByIdOrNull(concertSchedule.concertId)
                ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
            ConcertScheduleEntity.fromDomain(concertSchedule, concert)
        }
        val saved = concertScheduleJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): ConcertSchedule? {
        return concertScheduleJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): ConcertSchedule {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
    }

    override fun findAllByConcertId(concertId: Long): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertId(concertId).map { it.toDomain() }
    }

    override fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findAvailableSchedules(concertId, fromDate).map { it.toDomain() }
    }
}
