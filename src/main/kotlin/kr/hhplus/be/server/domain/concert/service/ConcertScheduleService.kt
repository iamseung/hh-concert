package kr.hhplus.be.server.domain.concert.service

import kr.hhplus.be.server.domain.concert.model.ConcertSchedule
import kr.hhplus.be.server.domain.concert.repository.ConcertScheduleRepository
import org.springframework.stereotype.Service

@Service
class ConcertScheduleService(
    private val concertScheduleRepository: ConcertScheduleRepository,
) {

    fun findById(scheduleId: Long): ConcertSchedule {
        return concertScheduleRepository.findByIdOrThrow(scheduleId)
    }

    fun findByConcertId(concertId: Long): List<ConcertSchedule> {
        return concertScheduleRepository.findAllByConcertId(concertId)
    }

    fun findByConcertIdAndId(concertId: Long, scheduleId: Long): ConcertSchedule {
        return concertScheduleRepository.findByIdOrThrow(scheduleId)
    }
}
