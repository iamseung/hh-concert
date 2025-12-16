package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import kr.hhplus.be.server.infrastructure.cache.ConcertCacheService
import org.springframework.stereotype.Service

@Service
class GetConcertsUseCase(
    private val concertRepository: ConcertRepository,
    private val concertCacheService: ConcertCacheService,
) {

    fun execute(): GetConcertsResult {
        // 캐시에서 조회 시도
        concertCacheService.getConcerts()?.let { return it }

        // 캐시 미스 시 DB 조회
        val concerts = concertRepository.findAll()
        val result = GetConcertsResult(
            concerts = concerts.map { concert ->
                GetConcertsResult.ConcertInfo(
                    concertId = concert.id,
                    title = concert.title,
                    description = concert.description,
                )
            },
        )

        // 캐시에 저장 (TTL: 1시간)
        concertCacheService.setConcerts(result)

        return result
    }
}
