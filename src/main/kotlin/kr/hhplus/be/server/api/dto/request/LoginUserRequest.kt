package kr.hhplus.be.server.api.dto.request

import kr.hhplus.be.server.application.usecase.user.LoginUserCommand

data class LoginUserRequest(
    val email: String,
    val password: String,
) {
    fun toCommand(): LoginUserCommand {
        return LoginUserCommand(
            email = email,
            password = password,
        )
    }
}
