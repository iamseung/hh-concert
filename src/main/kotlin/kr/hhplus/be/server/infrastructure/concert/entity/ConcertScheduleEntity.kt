package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDate

@Entity
@Table(name = "concert_schedule")
class ConcertScheduleEntity(
    @ManyToOne
    @JoinColumn(name = "concert_id", nullable = false)
    val concertEntity: ConcertEntity,

    val concertDate: LocalDate,

    @OneToMany(mappedBy = "concertScheduleEntity")
    val seatEntities: MutableList<SeatEntity> = mutableListOf(),
) : BaseEntity() {

    val isAvailable: Boolean
        get() = !concertDate.isBefore(LocalDate.now())

    fun validateAvailable() {
        if (!isAvailable) {
            throw BusinessException(ErrorCode.CONCERT_SCHEDULE_EXPIRED)
        }
    }
}
