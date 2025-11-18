package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.ConcertSchedule
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import java.time.LocalDate

@Entity
@Table(name = "concert_schedule")
class ConcertScheduleEntity(
    @ManyToOne
    @JoinColumn(name = "concert_id", nullable = false)
    val concertEntity: ConcertEntity,

    var concertDate: LocalDate,

    @OneToMany(mappedBy = "concertScheduleEntity")
    val seatEntities: MutableList<SeatEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): ConcertSchedule {
        return ConcertSchedule.reconstitute(
            id = id,
            concertId = concertEntity.id,
            concertDate = concertDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(concertSchedule: ConcertSchedule) {
        this.concertDate = concertSchedule.concertDate
    }

    companion object {
        fun fromDomain(
            concertSchedule: ConcertSchedule,
            concertEntity: ConcertEntity,
        ): ConcertScheduleEntity {
            return ConcertScheduleEntity(
                concertEntity = concertEntity,
                concertDate = concertSchedule.concertDate,
            )
        }
    }
}
