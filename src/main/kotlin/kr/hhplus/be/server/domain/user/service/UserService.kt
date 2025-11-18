package kr.hhplus.be.server.domain.user.service

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.domain.user.repository.UserRepository
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import kr.hhplus.be.server.infrastructure.user.repository.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userJpaRepository: UserJpaRepository,
) {
    fun getUser(userId: Long): User {
        return userRepository.findByIdOrThrow(userId)
    }

    fun getUserEntity(userId: Long): UserEntity {
        return userJpaRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    }
}
