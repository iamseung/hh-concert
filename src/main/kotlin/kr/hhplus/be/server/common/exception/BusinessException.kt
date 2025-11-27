package kr.hhplus.be.server.common.exception

/**
 * 비즈니스 로직 예외 (400 Bad Request)
 *
 * 사용 예:
 * - throw BusinessException(ErrorCode.INSUFFICIENT_POINTS)
 * - throw BusinessException(ErrorCode.SEAT_ALREADY_RESERVED)
 * - throw BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT)
 */
open class BusinessException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
