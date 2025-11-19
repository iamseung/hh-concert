package kr.hhplus.be.server.domain.user.model

import java.time.LocalDateTime

class UserModel private constructor(
    var id: Long,
    val userName: String,
    val email: String,
    val password: String,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    companion object {
        fun create(userName: String, email: String, password: String): UserModel {
            val now = LocalDateTime.now()
            return UserModel(
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
        ): UserModel {
            return UserModel(
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
