package kr.hhplus.be.server.domain.concert.model

import java.time.LocalDateTime

class ConcertModel private constructor(
    var id: Long,
    val title: String,
    val description: String?,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    companion object {
        fun create(title: String, description: String?): ConcertModel {
            val now = LocalDateTime.now()
            return ConcertModel(
                id = 0L,
                title = title,
                description = description,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            title: String,
            description: String?,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): ConcertModel {
            return ConcertModel(
                id = id,
                title = title,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
