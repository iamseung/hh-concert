package kr.hhplus.be.server.concert.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import kr.hhplus.be.server.common.BaseEntity
import java.util.Date

@Entity
class ConcertScheduled(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "concert_id", nullable = false)
    val concert: Concert,

    val concertDate: Date,

    @OneToMany(mappedBy = "concertScheduled")
    val seats: MutableList<Seat> = mutableListOf(),

) : BaseEntity() {
}