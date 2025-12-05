package kr.hhplus.be.server.application.usecase.user

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.config.JwtProperties
import kr.hhplus.be.server.config.PasswordEncoder
import kr.hhplus.be.server.domain.auth.JwtTokenProvider
import kr.hhplus.be.server.domain.auth.repository.RefreshTokenRepository
import kr.hhplus.be.server.domain.user.service.UserService
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class LoginUserUseCase(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {

    fun execute(command: LoginUserCommand): LoginUserResult {
        val user = userService.findByEmail(command.email)
            ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(command.password, user.password)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }

        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.email)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        refreshTokenRepository.save(
            userId = user.id,
            refreshToken = refreshToken,
            expiration = Duration.ofMillis(jwtProperties.refreshTokenValidity)
        )

        return LoginUserResult(
            userId = user.id,
            userEmail = user.email,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }
}