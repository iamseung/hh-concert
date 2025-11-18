package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.Concert
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "concert")
class ConcertEntity(
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String?,

    @OneToMany(mappedBy = "concertEntity")
    var concertScheduleEntities: MutableList<ConcertScheduleEntity> = mutableListOf(),
) : BaseEntity() {

    fun toDomain(): Concert {
        return Concert.reconstitute(
            id = id,
            title = title,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(concert: Concert) {
        this.title = concert.title
        this.description = concert.description
    }

    companion object {
        fun fromDomain(concert: Concert): ConcertEntity {
            return ConcertEntity(
                title = concert.title,
                description = concert.description,
            )
        }
    }
}
