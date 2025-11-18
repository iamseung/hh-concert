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
        val entity = if (user.id != 0L) {
            userJpaRepository.findByIdOrNull(user.id)?.apply {
                updateFromDomain(user)
            } ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        } else {
            UserEntity.fromDomain(user)
        }
        val saved = userJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: Long): User? {
        return userJpaRepository.findByIdOrNull(id)?.toDomain()
    }

    override fun findByIdOrThrow(id: Long): User {
        return findById(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    override fun findByEmail(email: String): User? {
        return userJpaRepository.findByEmail(email)?.toDomain()
    }

    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }
}
