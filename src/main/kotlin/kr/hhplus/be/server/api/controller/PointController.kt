package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.request.ChargePointRequest
import kr.hhplus.be.server.api.dto.response.PointResponse
import kr.hhplus.be.server.application.usecase.point.ChargePointCommand
import kr.hhplus.be.server.application.usecase.point.ChargePointUseCase
import kr.hhplus.be.server.application.usecase.point.GetPointCommand
import kr.hhplus.be.server.application.usecase.point.GetPointUseCase
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Points", description = "포인트 충전 및 조회")
class PointController(
    private val getPointUseCase: GetPointUseCase,
    private val chargePointUseCase: ChargePointUseCase,
) {

    @Operation(
        summary = "포인트 조회",
        description = "사용자 식별자를 통해 해당 사용자의 포인트를 조회합니다.",
        operationId = "getPoints",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PointResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/points")
    fun getPoints(
        @Parameter(description = "사용자 ID", required = true, `in` = ParameterIn.QUERY)
        @RequestParam userId: Long,
    ): PointResponse {
        val command = GetPointCommand(userId = userId)
        val result = getPointUseCase.execute(command)

        return PointResponse(
            id = null,
            userId = result.userId,
            balance = result.balance
        )
    }

    @Operation(
        summary = "포인트 충전",
        description = "사용자 식별자 및 충전할 금액을 받아 포인트를 충전합니다.",
        operationId = "chargePoints",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "충전 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PointResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (충전 금액이 0 이하 등)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/points/charge")
    fun chargePoint(
        @RequestBody request: ChargePointRequest,
    ): PointResponse {
        val command = ChargePointCommand(
            userId = request.userId,
            amount = request.amount
        )
        val result = chargePointUseCase.execute(command)

        return PointResponse(
            id = null,
            userId = result.userId,
            balance = result.balance
        )
    }
}
