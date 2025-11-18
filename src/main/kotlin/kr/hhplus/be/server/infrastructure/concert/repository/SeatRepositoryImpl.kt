package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import kr.hhplus.be.server.domain.concert.repository.SeatRepository
import kr.hhplus.be.server.infrastructure.concert.entity.SeatEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SeatRepositoryImpl(
    private val seatJpaRepository: SeatJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
) : SeatRepository {

    override fun save(seat: Seat): Seat {
        val entity = if (seat.id != 0L) {
            seatJpaRepository.findByIdOrNull(seat.id)?.apply {
                updateFromDomain(seat)
            } ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
        } else {
            val concertSchedule = concertScheduleJpaRepository.findByIdOrNull(seat.concertScheduleId)
                ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)
            SeatEntity.fromDomain(seat, concertSchedule)
        }
        val saved = seatJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): Seat? {
        return seatJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): Seat {
        return findById(id) ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
    }

    override fun findByIdWithLock(id: Long): Seat? {
        return seatJpaRepository.findByIdWithLock(id)?.toDomain()
    }

    override fun findAllByConcertScheduleId(concertScheduleId: Long): List<Seat> {
        return seatJpaRepository.findAllByConcertScheduleId(concertScheduleId).map { it.toDomain() }
    }

    override fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<Seat> {
        return seatJpaRepository.findAllByConcertScheduleIdAndSeatStatus(concertScheduleId, status).map { it.toDomain() }
    }
}
