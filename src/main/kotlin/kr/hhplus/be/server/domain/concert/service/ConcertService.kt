package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.Concert
import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import org.springframework.stereotype.Service

@Service
class ConcertService(
    private val concertRepository: ConcertRepository,
) {

    fun findById(concertId: Long): Concert {
        return concertRepository.findByIdOrThrow(concertId)
    }
}
