package kr.hhplus.be.server.api.dto.request

import kr.hhplus.be.server.application.usecase.user.SignUpUserCommand

data class SignUpUserRequest(
    val userName: String,
    val email: String,
    val password: String,
) {
    fun toCommand(): SignUpUserCommand {
        return SignUpUserCommand(
            userName = userName,
            email = email,
            password = password,
        )
    }
}
