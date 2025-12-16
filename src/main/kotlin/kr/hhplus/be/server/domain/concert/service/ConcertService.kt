package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import kr.hhplus.be.server.infrastructure.cache.ConcertCacheService
import org.springframework.stereotype.Service

@Service
class ConcertService(
    private val concertRepository: ConcertRepository,
    private val concertCacheService: ConcertCacheService,
) {

    fun findById(concertId: Long): ConcertModel {
        // 캐시에서 조회 시도
        concertCacheService.getConcert(concertId)?.let { return it }

        // 캐시 미스 시 DB 조회
        val concert = concertRepository.findByIdOrThrow(concertId)

        // 캐시에 저장 (TTL: 1시간)
        concertCacheService.setConcert(concert)

        return concert
    }
}
