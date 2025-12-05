package kr.hhplus.be.server.api.dto.response

import kr.hhplus.be.server.application.usecase.user.SignUpUserResult

data class SignUpUserResponse(
    val userId: Long,
) {
    companion object {
        fun from(result: SignUpUserResult): SignUpUserResponse {
            return SignUpUserResponse(userId = result.userId)
        }
    }
}
