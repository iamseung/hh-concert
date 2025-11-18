package kr.hhplus.be.server.infrastructure.concert.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.concert.model.Seat
import kr.hhplus.be.server.domain.concert.repository.SeatRepository
import kr.hhplus.be.server.infrastructure.concert.entity.SeatEntity
import kr.hhplus.be.server.domain.concert.model.SeatStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SeatRepositoryImpl(
    private val seatJpaRepository: SeatJpaRepository,
    private val concertScheduleJpaRepository: ConcertScheduleJpaRepository,
) : SeatRepository {

    override fun save(seat: Seat): Seat {
        val entity = toEntity(seat)
        val saved = seatJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): Seat? {
        return seatJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): Seat {
        return findById(id) ?: throw BusinessException(ErrorCode.SEAT_NOT_FOUND)
    }

    override fun findByIdWithLock(id: Long): Seat? {
        return seatJpaRepository.findByIdWithLock(id)?.let { toDomain(it) }
    }

    override fun findAllByConcertScheduleId(concertScheduleId: Long): List<Seat> {
        return seatJpaRepository.findAllByConcertScheduleId(concertScheduleId).map { toDomain(it) }
    }

    override fun findAllByConcertScheduleIdAndStatus(concertScheduleId: Long, status: SeatStatus): List<Seat> {
        return seatJpaRepository.findAllByConcertScheduleIdAndSeatStatus(concertScheduleId, status).map { toDomain(it) }
    }

    private fun toDomain(seat: SeatEntity): Seat {
        return Seat.reconstitute(
            id = seat.id,
            concertScheduleId = seat.concertScheduleEntity.id,
            seatNumber = seat.seatNumber,
            seatStatus = seat.seatStatus,
            price = seat.price,
            createdAt = seat.createdAt,
            updatedAt = seat.updatedAt,
        )
    }

    private fun toEntity(seat: Seat): SeatEntity {
        val concertSchedule = concertScheduleJpaRepository.findByIdOrNull(seat.concertScheduleId)
            ?: throw BusinessException(ErrorCode.CONCERT_SCHEDULE_NOT_FOUND)

        val entity = SeatEntity(
            concertScheduleEntity = concertSchedule,
            seatNumber = seat.seatNumber,
            seatStatus = seat.seatStatus,
            price = seat.price,
        )
        seat.getId()?.let { entity.id = it }
        return entity
    }
}
