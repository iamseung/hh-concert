package kr.hhplus.be.server.infrastructure.persistence.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ConcertRepositoryImpl(
    private val concertJpaRepository: ConcertJpaRepository,
) : ConcertRepository {

    override fun findById(id: Long): ConcertModel? {
        return concertJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): ConcertModel {
        return findById(id) ?: throw BusinessException(ErrorCode.CONCERT_NOT_FOUND)
    }

    override fun findAll(): List<ConcertModel> {
        return concertJpaRepository.findAll().map { it.toModel() }
    }
}
