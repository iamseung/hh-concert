package kr.hhplus.be.server.application.usecase.user

data class LoginUserCommand(
    val email: String,
    val password: String,
)
