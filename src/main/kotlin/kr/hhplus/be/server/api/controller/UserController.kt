package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.LoginUserRequest
import kr.hhplus.be.server.api.dto.request.RefreshTokenRequest
import kr.hhplus.be.server.api.dto.request.SignUpUserRequest
import kr.hhplus.be.server.api.dto.response.LoginUserResponse
import kr.hhplus.be.server.api.dto.response.RefreshTokenResponse
import kr.hhplus.be.server.api.dto.response.SignUpUserResponse
import kr.hhplus.be.server.application.usecase.auth.RefreshTokenUseCase
import kr.hhplus.be.server.application.usecase.user.LoginUserUseCase
import kr.hhplus.be.server.application.usecase.user.SignUpUserUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "사용자 관리")
class UserController(
    private val loginUserUseCase: LoginUserUseCase,
    private val signUpUserUseCase: SignUpUserUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
) {

    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호로 로그인을 처리하고 JWT 토큰을 발급합니다."
    )
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginUserRequest
    ): LoginUserResponse {
        val result = loginUserUseCase.execute(request.toCommand())
        return LoginUserResponse.from(result)
    }

    @Operation(
        summary = "회원가입",
        description = "입력받은 정보를 기반으로 회원을 생성합니다."
    )
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(
        @RequestBody request: SignUpUserRequest
    ): SignUpUserResponse {
        val result = signUpUserUseCase.execute(request.toCommand())
        return SignUpUserResponse.from(result)
    }

    @Operation(
        summary = "토큰 갱신",
        description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다."
    )
    @PostMapping("/refresh")
    fun refreshToken(
        @RequestBody request: RefreshTokenRequest
    ): RefreshTokenResponse {
        val result = refreshTokenUseCase.execute(request.toCommand())
        return RefreshTokenResponse.from(result)
    }
}