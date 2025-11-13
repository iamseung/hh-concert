package kr.hhplus.be.server.concert.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import kr.hhplus.be.server.common.BaseEntity

@Entity
class Concert(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val title: String,

    @Column(columnDefinition = "TEXT")
    val description: String?,

    @OneToMany(mappedBy = "concert")
    var concertSchedules: MutableList<ConcertScheduled> = mutableListOf(),
) : BaseEntity() {
}