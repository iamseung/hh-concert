package kr.hhplus.be.server.application.usecase.auth

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.domain.auth.JwtTokenProvider
import kr.hhplus.be.server.domain.auth.repository.RefreshTokenRepository
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component

@Component
class RefreshTokenUseCase(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userService: UserService,
) {

    fun execute(command: RefreshTokenCommand): RefreshTokenResult {
        if (!jwtTokenProvider.validateToken(command.refreshToken)) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }

        val userId = jwtTokenProvider.getUserIdFromToken(command.refreshToken)

        val storedToken = refreshTokenRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.TOKEN_EXPIRED)

        if (storedToken != command.refreshToken) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }

        val user = userService.findById(userId)

        val newAccessToken = jwtTokenProvider.generateAccessToken(user.id, user.email)

        return RefreshTokenResult(accessToken = newAccessToken)
    }
}
