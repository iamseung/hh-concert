package kr.hhplus.be.server.infrastructure.user.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    override fun save(user: User): User {
        val entity = toEntity(user)
        val saved = userJpaRepository.save(entity)
        return toDomain(saved)
    }

    override fun findById(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)?.let { toDomain(it) }
    }

    override fun findByIdOrThrow(id: Long): User {
        return findById(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    override fun findByEmail(email: String): User? {
        return userJpaRepository.findByEmail(email)?.let { toDomain(it) }
    }

    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }

    private fun toDomain(user: UserEntity): User {
        return User.reconstitute(
            id = user.id,
            userName = user.userName,
            email = user.email,
            password = user.password,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
        )
    }

    private fun toEntity(user: User): UserEntity {
        val entity = UserEntity(
            userName = user.userName,
            email = user.email,
            password = user.password,
        )
        return entity
    }
}