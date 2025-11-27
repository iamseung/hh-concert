package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.IssueQueueTokenRequest
import kr.hhplus.be.server.api.dto.response.QueueStatusResponse
import kr.hhplus.be.server.api.dto.response.QueueTokenResponse
import kr.hhplus.be.server.application.QueueFacade
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
@Tag(name = "Queue", description = "대기열 토큰 관리")
class QueueController(
    private val queueFacade: QueueFacade,
) {

    @Operation(
        summary = "대기열 토큰 발급",
        description = """서비스를 이용할 토큰을 발급받습니다.

**토큰 구성**:
- 유저의 UUID
- 대기 순서 또는 잔여 시간 정보

**중요**: 이후 모든 API는 이 토큰을 통해 대기열 검증을 통과해야 이용 가능합니다.""",
        operationId = "issueQueueToken",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "토큰 발급 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = QueueTokenResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueQueueToken(
        @RequestBody request: IssueQueueTokenRequest,
    ): QueueTokenResponse {
        return queueFacade.issueQueueToken(request.userId)
    }

    @Operation(
        summary = "대기 번호 조회",
        description = """현재 대기열 상태를 조회합니다 (폴링 방식).

**반환 정보**:
- 대기 순서
- 대기 상태 (WAITING, ACTIVE, EXPIRED)
- 예상 대기 시간 (선택)""",
        operationId = "getQueueStatus",
        security = [SecurityRequirement(name = "QueueToken")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = QueueStatusResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (유효하지 않은 토큰)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "대기열 정보를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/status")
    fun getQueueStatus(
        @Parameter(description = "대기열 토큰 (UUID 형식)", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("X-Queue-Token") queueToken: String,
    ): QueueStatusResponse {
        return queueFacade.getQueueStatus(queueToken)
    }
}
