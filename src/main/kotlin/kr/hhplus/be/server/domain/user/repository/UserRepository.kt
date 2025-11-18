package kr.hhplus.be.server.domain.user.repository

import kr.hhplus.be.server.domain.user.model.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByIdOrThrow(id: Long): User
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
