package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.IssueQueueTokenRequest
import kr.hhplus.be.server.api.dto.response.QueueStatusResponse
import kr.hhplus.be.server.api.dto.response.QueueTokenResponse
import kr.hhplus.be.server.application.usecase.queue.GetQueueStatusCommand
import kr.hhplus.be.server.application.usecase.queue.GetQueueStatusUseCase
import kr.hhplus.be.server.application.usecase.queue.IssueQueueTokenCommand
import kr.hhplus.be.server.application.usecase.queue.IssueQueueTokenUseCase
import kr.hhplus.be.server.domain.queue.model.QueueStatus
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
    private val issueQueueTokenUseCase: IssueQueueTokenUseCase,
    private val getQueueStatusUseCase: GetQueueStatusUseCase,
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
    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueQueueToken(
        @RequestBody request: IssueQueueTokenRequest,
    ): QueueTokenResponse {
        val command = IssueQueueTokenCommand(userId = request.userId)
        val result = issueQueueTokenUseCase.execute(command)
        return QueueTokenResponse(
            userId = request.userId,
            token = result.token,
            queueStatus = result.status,
            queuePosition = result.position,
            activatedAt = null,
            expiresAt = null,
            createdAt = result.createdAt,
            updatedAt = result.createdAt,
        )
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
    @GetMapping("/status")
    fun getQueueStatus(
        @Parameter(description = "대기열 토큰 (UUID 형식)", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("X-Queue-Token") queueToken: String,
    ): QueueStatusResponse {
        val command = GetQueueStatusCommand(token = queueToken)
        val result = getQueueStatusUseCase.execute(command)
        val estimatedTime = if (result.status == QueueStatus.WAITING) {
            result.position
        } else {
            0L
        }
        return QueueStatusResponse(
            queuePosition = result.position,
            queueStatus = result.status,
            estimatedWaitTimeMinutes = estimatedTime,
        )
    }
}
