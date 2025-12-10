package kr.hhplus.be.server.infrastructure.persistence.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(
    name = "concert_schedule",
    indexes = [
        Index(name = "idx_schedule_concert_date", columnList = "concert_id, concert_date"),
    ],
)
class ConcertSchedule(
    @Column(name = "concert_id", nullable = false)
    val concertId: Long,

    var concertDate: LocalDateTime,
) : BaseEntity() {

    fun toModel(): ConcertScheduleModel {
        return ConcertScheduleModel.reconstitute(
            id = id,
            concertId = concertId,
            concertDate = concertDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(concertScheduleModel: ConcertScheduleModel) {
        this.concertDate = concertScheduleModel.concertDate
    }

    companion object {
        fun fromDomain(concertScheduleModel: ConcertScheduleModel): ConcertSchedule {
            return ConcertSchedule(
                concertId = concertScheduleModel.concertId,
                concertDate = concertScheduleModel.concertDate,
            )
        }
    }
}
