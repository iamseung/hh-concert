package kr.hhplus.be.server.user.domain.model

import java.time.LocalDateTime

class User private constructor(
    private var id: Long?,
    val userName: String,
    val email: String,
    val password: String,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun assignId(id: Long) {
        this.id = id
    }

    fun getId(): Long? = id
    fun getUserName(): String = userName
    fun getEmail(): String = email
    fun getPassword(): String = password
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt

    companion object {
        fun create(userName: String, email: String, password: String): User {
            val now = LocalDateTime.now()
            return User(
                id = null,
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
