package kr.hhplus.be.server.common.exception

/**
 * 리소스를 찾을 수 없을 때 발생하는 예외 (404 Not Found)
 *
 * 사용 예:
 * - throw NotFoundException(ErrorCode.CONCERT_NOT_FOUND)
 * - throw NotFoundException(ErrorCode.USER_NOT_FOUND)
 * - throw NotFoundException(ErrorCode.SEAT_NOT_FOUND)
 */
class NotFoundException(
    errorCode: ErrorCode,
) : BusinessException(errorCode)
