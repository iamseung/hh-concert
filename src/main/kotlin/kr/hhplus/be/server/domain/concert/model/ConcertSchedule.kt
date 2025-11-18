package kr.hhplus.be.server.domain.concert.model

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDate
import java.time.LocalDateTime

class ConcertSchedule private constructor(
    var id: Long,
    val concertId: Long,
    val concertDate: LocalDate,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    val isAvailable: Boolean
        get() = !concertDate.isBefore(LocalDate.now())

    fun validateAvailable() {
        if (!isAvailable) {
            throw BusinessException(ErrorCode.CONCERT_SCHEDULE_EXPIRED)
        }
    }

    fun validateIsConcert(concert: Concert) {
        if (this.concertId != concert.id) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    companion object {
        fun create(concertId: Long, concertDate: LocalDate): ConcertSchedule {
            val now = LocalDateTime.now()
            return ConcertSchedule(
                id = 0L,
                concertId = concertId,
                concertDate = concertDate,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            concertId: Long,
            concertDate: LocalDate,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): ConcertSchedule {
            return ConcertSchedule(
                id = id,
                concertId = concertId,
                concertDate = concertDate,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
