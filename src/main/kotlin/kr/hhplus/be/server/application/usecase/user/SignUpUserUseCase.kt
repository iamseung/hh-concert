package kr.hhplus.be.server.application.usecase.user

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.config.PasswordEncoder
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component

@Component
class SignUpUserUseCase(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun execute(command: SignUpUserCommand): SignUpUserResult {
        validateEmailNotDuplicated(command.email)

        val user = UserModel.create(
            userName = command.userName,
            email = command.email,
            password = passwordEncoder.encode(command.password),
        )

        return SignUpUserResult(userId = userService.save(user))
    }

    private fun validateEmailNotDuplicated(email: String) {
        if (userService.exists(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_USER)
        }
    }
}