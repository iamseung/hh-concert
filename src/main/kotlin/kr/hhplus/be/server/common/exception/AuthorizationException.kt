package kr.hhplus.be.server.common.exception

/**
 * 권한 없음 시 발생하는 예외 (403 Forbidden)
 *
 * 사용 예:
 * - throw AuthorizationException(ErrorCode.ACCESS_DENIED)
 * - throw AuthorizationException(ErrorCode.QUEUE_TOKEN_NOT_ACTIVE)
 */
class AuthorizationException(
    errorCode: ErrorCode,
) : BusinessException(errorCode)
