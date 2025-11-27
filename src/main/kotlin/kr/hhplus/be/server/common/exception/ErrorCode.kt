package kr.hhplus.be.server.common.exception

import org.springframework.http.HttpStatus

/**
 * 에러 코드 정의
 */
enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // Common Errors (400 Bad Request)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "잘못된 타입입니다"),
    MISSING_INPUT_VALUE(HttpStatus.BAD_REQUEST, "필수 입력값이 누락되었습니다"),

    // Resource Not Found (404)
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 엔티티를 찾을 수 없습니다"),
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "콘서트를 찾을 수 없습니다"),
    CONCERT_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "콘서트 일정을 찾을 수 없습니다"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "콘서트 일정을 찾을 수 없습니다"),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다"),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 정보를 찾을 수 없습니다"),
    QUEUE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "대기열 토큰을 찾을 수 없습니다"),

    // Authentication & Authorization (401, 403)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    QUEUE_TOKEN_NOT_ACTIVE(HttpStatus.FORBIDDEN, "대기열 토큰이 활성 상태가 아닙니다"),

    // Business Logic Errors (400)
    SEAT_ALREADY_RESERVED(HttpStatus.BAD_REQUEST, "이미 예약된 좌석입니다"),
    SEAT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "예약 가능한 좌석이 아닙니다"),
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "포인트가 부족합니다"),
    INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "충전 금액이 올바르지 않습니다"),
    CONCERT_SCHEDULE_EXPIRED(HttpStatus.BAD_REQUEST, "콘서트가 만료되었습니다"),
    INVALID_RESERVATION(HttpStatus.BAD_REQUEST, "유효하지 않은 예약입니다"),
    INVALID_RESERVATION_STATUS(HttpStatus.BAD_REQUEST, "예약 상태가 올바르지 않습니다"),
    RESERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "예약이 만료되었습니다"),
    RESERVATION_ALREADY_CONFIRMED(HttpStatus.BAD_REQUEST, "이미 확정된 예약입니다"),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 결제가 완료되었습니다"),

    // Server Errors (500)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 오류가 발생했습니다"),
}
