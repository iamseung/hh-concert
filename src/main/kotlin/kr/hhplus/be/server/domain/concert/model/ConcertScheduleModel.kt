package kr.hhplus.be.server.domain.concert.model

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDateTime

class ConcertScheduleModel private constructor(
    var id: Long,
    val concertId: Long,
    val concertDate: LocalDateTime,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    val isAvailable: Boolean
        get() = !concertDate.isBefore(LocalDateTime.now())

    fun validateAvailable() {
        if (!isAvailable) {
            throw BusinessException(ErrorCode.CONCERT_SCHEDULE_EXPIRED)
        }
    }

    fun validateIsConcert(concertModel: ConcertModel) {
        if (this.concertId != concertModel.id) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
    }

    companion object {
        fun create(concertId: Long, concertDate: LocalDateTime): ConcertScheduleModel {
            val now = LocalDateTime.now()
            return ConcertScheduleModel(
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
            concertDate: LocalDateTime,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): ConcertScheduleModel {
            return ConcertScheduleModel(
                id = id,
                concertId = concertId,
                concertDate = concertDate,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
