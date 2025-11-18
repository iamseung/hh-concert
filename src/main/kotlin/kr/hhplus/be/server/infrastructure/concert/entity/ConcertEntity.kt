package kr.hhplus.be.server.infrastructure.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "concert")
class ConcertEntity(
    val title: String,

    @Column(columnDefinition = "TEXT")
    val description: String?,

    @OneToMany(mappedBy = "concertEntity")
    var concertScheduleEntities: MutableList<ConcertScheduleEntity> = mutableListOf(),
) : BaseEntity()
