package kr.hhplus.be.server.domain.user.model

import java.time.LocalDateTime

class User private constructor(
    var id: Long,
    val userName: String,
    val email: String,
    val password: String,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun assignId(id: Long) {
        this.id = id
    }

    companion object {
        fun create(userName: String, email: String, password: String): User {
            val now = LocalDateTime.now()
            return User(
                id = 0L,
                userName = userName,
                email = email,
                password = password,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            userName: String,
            email: String,
            password: String,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): User {
            return User(
                id = id,
                userName = userName,
                email = email,
                password = password,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
