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
        val entity = if (concert.id != 0L) {
            // 기존 엔티티 조회 후 업데이트
            concertJpaRepository.findByIdOrNull(concert.id)?.apply {
                updateFromDomain(concert)
            } ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
        } else {
            // 새로운 엔티티 생성
            ConcertEntity.fromDomain(concert)
        }
        val saved = concertJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): Concert? {
        return concertJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): Concert {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
    }

    override fun findAll(): List<Concert> {
        return concertJpaRepository.findAll().map { it.toDomain() }
    }
}
