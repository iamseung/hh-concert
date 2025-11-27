package kr.hhplus.be.server.domain.user.service

import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    fun findById(userId: Long): UserModel {
        return userRepository.findByIdOrThrow(userId)
    }
}
