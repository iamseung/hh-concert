package kr.hhplus.be.server.application.usecase.user

data class LoginUserResult(
    val userId: Long,
    val userEmail: String,
    val accessToken: String,
    val refreshToken: String,
)
