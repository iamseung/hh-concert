package kr.hhplus.be.server.infrastructure.persistence.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.concert.model.ConcertModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "concert")
class Concert(
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String?,
) : BaseEntity() {

    fun toModel(): ConcertModel {
        return ConcertModel.reconstitute(
            id = id,
            title = title,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(concertModel: ConcertModel) {
        this.title = concertModel.title
        this.description = concertModel.description
    }

    companion object {
        fun fromDomain(concertModel: ConcertModel): Concert {
            return Concert(
                title = concertModel.title,
                description = concertModel.description,
            )
        }
    }
}
