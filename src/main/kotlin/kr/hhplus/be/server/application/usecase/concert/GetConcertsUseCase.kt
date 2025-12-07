package kr.hhplus.be.server.application.usecase.concert

import kr.hhplus.be.server.domain.concert.repository.ConcertRepository
import org.springframework.stereotype.Service

@Service
class GetConcertsUseCase(
    private val concertRepository: ConcertRepository,
) {

    fun execute(): GetConcertsResult {
        val concerts = concertRepository.findAll()
        return GetConcertsResult(
            concerts = concerts.map { concert ->
                GetConcertsResult.ConcertInfo(
                    concertId = concert.id,
                    title = concert.title,
                    description = concert.description,
                )
            },
        )
    }
}
