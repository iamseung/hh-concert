package kr.hhplus.be.server.common.exception

import jakarta.servlet.http.HttpServletRequest
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 전역 예외 처리 핸들러
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Business exception occurred: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = ex.errorCode.status.value(),
            error = ex.errorCode.status.reasonPhrase,
            message = ex.message ?: ex.errorCode.message,
            path = request.requestURI,
        )

        return ResponseEntity
            .status(ex.errorCode.status)
            .body(errorResponse)
    }

    /**
     * 404 Not Found 예외 처리
     */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(
        ex: NoHandlerFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("No handler found: ${ex.message}")

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = "요청하신 리소스를 찾을 수 없습니다",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(errorResponse)
    }

    /**
     * 지원하지 않는 HTTP 메서드 예외 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupportedException(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Method not supported: ${ex.method}")

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.METHOD_NOT_ALLOWED.value(),
            error = HttpStatus.METHOD_NOT_ALLOWED.reasonPhrase,
            message = "지원하지 않는 HTTP 메서드입니다: ${ex.method}",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(errorResponse)
    }

    /**
     * Validation 실패 예외 처리 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Validation failed: ${ex.bindingResult}")

        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = errorMessage.ifEmpty { "입력값 검증에 실패했습니다" },
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * Binding 실패 예외 처리
     */
    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Binding failed: ${ex.bindingResult}")

        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = errorMessage.ifEmpty { "요청 파라미터 바인딩에 실패했습니다" },
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch: ${ex.name} should be ${ex.requiredType?.simpleName}")

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "파라미터 타입이 올바르지 않습니다: ${ex.name}",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * 필수 파라미터 누락 예외 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Missing parameter: ${ex.parameterName}")

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "필수 파라미터가 누락되었습니다: ${ex.parameterName}",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * HTTP 메시지 읽기 실패 예외 처리 (JSON 파싱 오류 등)
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("HTTP message not readable: ${ex.message}")

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * IllegalArgumentException 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "잘못된 요청입니다",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * IllegalStateException 예외 처리
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal state: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "요청을 처리할 수 없는 상태입니다",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(errorResponse)
    }

    /**
     * 기타 모든 예외 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected exception occurred: ${ex.message}", ex)

        val errorResponse = ErrorResponse(
            timestamp = getCurrentTimestamp(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = "서버 내부 오류가 발생했습니다",
            path = request.requestURI,
        )

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse)
    }

    /**
     * 현재 시간을 UTC ISO-8601 형식으로 반환
     */
    private fun getCurrentTimestamp(): String {
        return ZonedDateTime.now()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
