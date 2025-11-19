package kr.hhplus.be.server.domain.user.repository

import kr.hhplus.be.server.domain.user.model.UserModel

interface UserRepository {
    fun save(userModel: UserModel): UserModel
    fun findById(id: Long): UserModel?
    fun findByIdOrThrow(id: Long): UserModel
    fun findByEmail(email: String): UserModel?
    fun existsByEmail(email: String): Boolean
}
