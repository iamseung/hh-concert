package kr.hhplus.be.server.application.usecase.user

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.config.PasswordEncoder
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.domain.point.repository.PointRepository
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SignUpUserUseCase(
    private val userService: UserService,
    private val pointRepository: PointRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun execute(command: SignUpUserCommand): SignUpUserResult {
        validateEmailNotDuplicated(command.email)

        val user = UserModel.create(
            userName = command.userName,
            email = command.email,
            password = passwordEncoder.encode(command.password),
        )

        val userId = userService.save(user)

        // 포인트 초기화 (0원으로 시작)
        val point = PointModel.create(userId = userId, balance = 0)
        pointRepository.save(point)

        return SignUpUserResult(userId = userId)
    }

    private fun validateEmailNotDuplicated(email: String) {
        if (userService.exists(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_USER)
        }
    }
}