package kr.hhplus.be.server.application.usecase.user

data class SignUpUserCommand(
    val userName: String,
    val email: String,
    val password: String,
)
