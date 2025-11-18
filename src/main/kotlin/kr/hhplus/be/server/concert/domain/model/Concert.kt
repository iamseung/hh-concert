package kr.hhplus.be.server.concert.domain.model

import java.time.LocalDateTime

class Concert private constructor(
    private var id: Long?,
    val title: String,
    val description: String?,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun assignId(id: Long) {
        this.id = id
    }

    fun getId(): Long? = id
    fun getTitle(): String = title
    fun getDescription(): String? = description
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt

    companion object {
        fun create(title: String, description: String?): Concert {
            val now = LocalDateTime.now()
            return Concert(
                id = null,
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
