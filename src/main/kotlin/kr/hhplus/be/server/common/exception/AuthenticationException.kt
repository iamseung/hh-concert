package kr.hhplus.be.server.common.exception

/**
 * 인증 실패 시 발생하는 예외 (401 Unauthorized)
 *
 * 사용 예:
 * - throw AuthenticationException(ErrorCode.UNAUTHORIZED)
 * - throw AuthenticationException(ErrorCode.INVALID_TOKEN)
 * - throw AuthenticationException(ErrorCode.TOKEN_EXPIRED)
 */
class AuthenticationException(
    errorCode: ErrorCode,
) : BusinessException(errorCode)
