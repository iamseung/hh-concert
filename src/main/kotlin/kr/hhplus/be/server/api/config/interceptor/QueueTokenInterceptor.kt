package kr.hhplus.be.server.api.config.interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.hhplus.be.server.application.QueueFacade
import kr.hhplus.be.server.common.exception.AuthenticationException
import kr.hhplus.be.server.common.exception.ErrorCode
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 대기열 토큰 검증 인터셉터
 *
 * X-Queue-Token 헤더를 검증하여 ACTIVE 상태인 토큰만 API 접근 허용
 */
@Component
class QueueTokenInterceptor(
    private val queueFacade: QueueFacade,
) : HandlerInterceptor {

    companion object {
        private const val QUEUE_TOKEN_HEADER = "X-Queue-Token"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val token = request.getHeader(QUEUE_TOKEN_HEADER)
            ?: throw AuthenticationException(ErrorCode.INVALID_TOKEN)

        // 토큰 검증 (ACTIVE 상태가 아니면 예외 발생)
        queueFacade.validateQueueToken(token)

        return true
    }
}
