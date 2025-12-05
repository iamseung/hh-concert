package kr.hhplus.be.server.api.dto.request

import kr.hhplus.be.server.application.usecase.auth.RefreshTokenCommand

data class RefreshTokenRequest(
    val refreshToken: String,
) {
    fun toCommand(): RefreshTokenCommand {
        return RefreshTokenCommand(refreshToken = refreshToken)
    }
}
