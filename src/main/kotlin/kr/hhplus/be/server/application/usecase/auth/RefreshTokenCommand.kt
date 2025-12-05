package kr.hhplus.be.server.application.usecase.auth

data class RefreshTokenCommand(
    val refreshToken: String,
)
