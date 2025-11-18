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
import kr.hhplus.be.server.api.dto.request.CreateReservationRequest
import kr.hhplus.be.server.api.dto.response.ReservationResponse
import kr.hhplus.be.server.application.ReservationFacade
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/concerts")
@Tag(name = "Reservations", description = "좌석 예약 관리")
class ReservationController(
    private val reservationService: ReservationFacade,
) {

    @GetMapping("/{concertId}/reservations")
    fun getConcertReservations(
        @PathVariable concertId: Long,
        @RequestHeader("User-Id") userId: Long,
    ): List<ReservationResponse> {
        return reservationService.getConcertReservations(userId)
    }

    @Operation(
        summary = "좌석 예약 요청",
        description = """날짜와 좌석 정보를 입력받아 좌석을 예약합니다.

**중요**:
- 좌석 예약 시 해당 좌석은 5분간 임시 배정됩니다.
- 5분 내 결제 미완료 시 임시 배정이 해제되어 다른 사용자가 예약 가능합니다.
- 대기열 토큰이 ACTIVE 상태여야 예약 가능합니다.""",
        operationId = "createReservation",
        security = [SecurityRequirement(name = "QueueToken")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "예약 성공 (임시 배정)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ReservationResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (이미 예약된 좌석, 포인트 부족 등)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
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
                responseCode = "403",
                description = "권한 없음 (대기열 토큰이 ACTIVE 상태가 아님)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "콘서트를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{concertId}/reservations")
    fun createReservation(
        @Parameter(description = "콘서트 ID", required = true)
        @PathVariable concertId: Long,
        @Parameter(description = "대기열 토큰 (UUID 형식)", required = true, `in` = ParameterIn.HEADER)
        @RequestHeader("X-Queue-Token") queueToken: String,
        @RequestBody request: CreateReservationRequest,
    ): ReservationResponse {
        return reservationService.createReservation(request.userId, request.scheduleId, request.seatId, queueToken)
    }
}
