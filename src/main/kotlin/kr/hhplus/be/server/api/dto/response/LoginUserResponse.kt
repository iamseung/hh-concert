package kr.hhplus.be.server.api.dto.response

import kr.hhplus.be.server.application.usecase.user.LoginUserResult

data class LoginUserResponse(
    val userId: Long,
    val userEmail: String,
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        fun from(result: LoginUserResult): LoginUserResponse {
            return LoginUserResponse(
                userId = result.userId,
                userEmail = result.userEmail,
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
            )
        }
    }
}
