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
        val entity = toEntity(concertSchedule)
        val saved = concertScheduleJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): ConcertSchedule? {
        return concertScheduleJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): ConcertSchedule {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
    }

    override fun findAllByConcertId(concertId: Long): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findByConcertId(concertId).map { toDomain(it) }
    }

    override fun findAvailableSchedules(concertId: Long, fromDate: LocalDate): List<ConcertSchedule> {
        return concertScheduleJpaRepository.findAvailableSchedules(concertId, fromDate).map { toDomain(it) }
    }

    private fun toDomain(concertSchedule: ConcertScheduleEntity): ConcertSchedule {
        return ConcertSchedule.reconstitute(
            id = concertSchedule.id,
            concertId = concertSchedule.concertEntity.id,
            concertDate = concertSchedule.concertDate,
            createdAt = concertSchedule.createdAt,
            updatedAt = concertSchedule.updatedAt,
        )
    }

    private fun toEntity(concertSchedule: ConcertSchedule): ConcertScheduleEntity {
        val concert = concertJpaRepository.findByIdOrNull(concertSchedule.concertId)
            ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)

        val entity = ConcertScheduleEntity(
            concertEntity = concert,
            concertDate = concertSchedule.concertDate,
        )
        concertSchedule.getId()?.let { entity.id = it }
        return entity
    }
}
