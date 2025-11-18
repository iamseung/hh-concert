package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.Concert
import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import kr.hhplus.be.server.infrastructure.concert.entity.ConcertEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ConcertRepositoryImpl(
    private val concertJpaRepository: ConcertJpaRepository,
) : ConcertRepository {

    override fun save(concert: Concert): Concert {
        val entity = toEntity(concert)
        val saved = concertJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Concert? {
        return concertJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): Concert {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
    }

    override fun findAll(): List<Concert> {
        return concertJpaRepository.findAll().map { toDomain(it) }
    }

    private fun toDomain(concert: ConcertEntity): Concert {
        return Concert.reconstitute(
            id = concert.id,
            title = concert.title,
            description = concert.description,
            createdAt = concert.createdAt,
            updatedAt = concert.updatedAt,
        )
    }

    private fun toEntity(concert: Concert): ConcertEntity {
        val entity = ConcertEntity(
            title = concert.title,
            description = concert.description,
        )

        return entity
    }
}
