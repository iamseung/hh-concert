package kr.hhplus.be.server.infrastructure.persistence.user.repository

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {

    override fun save(userModel: UserModel): UserModel {
        val entity = if (userModel.id != 0L) {
            userJpaRepository.findByIdOrNull(userModel.id)?.apply {
                updateFromDomain(userModel)
            } ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        } else {
            User.fromDomain(userModel)
        }
        val saved = userJpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findById(id: Long): UserModel? {
        return userJpaRepository.findByIdOrNull(id)?.toModel()
    }

    override fun findByIdOrThrow(id: Long): UserModel {
        return findById(id) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }

    override fun findByEmail(email: String): UserModel? {
        return userJpaRepository.findByEmail(email)?.toModel()
    }

    override fun existsByEmail(email: String): Boolean {
        return userJpaRepository.existsByEmail(email)
    }
}
