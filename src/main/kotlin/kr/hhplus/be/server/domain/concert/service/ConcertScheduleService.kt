package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.domain.concert.repository.ConcertScheduleRepository
import org.springframework.stereotype.Service

@Service
class ConcertScheduleService(
    private val concertScheduleRepository: ConcertScheduleRepository,
) {

    fun findById(scheduleId: Long): ConcertScheduleModel {
        return concertScheduleRepository.findByIdOrThrow(scheduleId)
    }

    fun findByConcertId(concertId: Long): List<ConcertScheduleModel> {
        return concertScheduleRepository.findAllByConcertId(concertId)
    }
}
