package kr.hhplus.be.server.api.dto.response

import kr.hhplus.be.server.application.usecase.auth.RefreshTokenResult

data class RefreshTokenResponse(
    val accessToken: String,
) {
    companion object {
        fun from(result: RefreshTokenResult): RefreshTokenResponse {
            return RefreshTokenResponse(accessToken = result.accessToken)
        }
    }
}
