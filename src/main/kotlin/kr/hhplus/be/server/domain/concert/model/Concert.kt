package kr.hhplus.be.server.domain.concert.model

import java.time.LocalDateTime

class Concert private constructor(
    var id: Long,
    val title: String,
    val description: String?,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun assignId(id: Long) {
        this.id = id
    }

    companion object {
        fun create(title: String, description: String?): Concert {
            val now = LocalDateTime.now()
            return Concert(
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
        ): Concert {
            return Concert(
                id = id,
                title = title,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
