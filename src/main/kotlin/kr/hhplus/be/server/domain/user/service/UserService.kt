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

    fun findByEmail(email: String): UserModel? {
        return userRepository.findByEmail(email)
    }

    fun exists(email: String): Boolean {
        return userRepository.findByEmail(email) != null
    }

    fun save(user: UserModel): Long {
        return userRepository.save(user).id
    }
}
