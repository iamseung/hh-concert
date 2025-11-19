package kr.hhplus.be.server.infrastructure.persistence.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.ConcertScheduleModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDate

@Entity
@Table(name = "concert_schedule")
class ConcertSchedule(
    @ManyToOne
    @JoinColumn(name = "concert_id", nullable = false)
    val concert: Concert,

    var concertDate: LocalDate,

    @OneToMany(mappedBy = "concertSchedule")
    val seats: MutableList<Seat> = mutableListOf(),
) : BaseEntity() {

    fun toModel(): ConcertScheduleModel {
        return ConcertScheduleModel.reconstitute(
            id = id,
            concertId = concert.id,
            concertDate = concertDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(concertScheduleModel: ConcertScheduleModel) {
        this.concertDate = concertScheduleModel.concertDate
    }

    companion object {
        fun fromDomain(
            concertScheduleModel: ConcertScheduleModel,
            concert: Concert,
        ): ConcertSchedule {
            return ConcertSchedule(
                concert = concert,
                concertDate = concertScheduleModel.concertDate,
            )
        }
    }
}
