package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.response.ConcertScheduleResponse
import kr.hhplus.be.server.api.dto.response.SeatResponse
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSchedulesCommand
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSchedulesUseCase
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsCommand
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsUseCase
import kr.hhplus.be.server.common.dto.ErrorResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/concerts")
@Tag(name = "Concerts API", description = "콘서트 조회")
class ConcertController(
    private val getAvailableSchedulesUseCase: GetAvailableSchedulesUseCase,
    private val getAvailableSeatsUseCase: GetAvailableSeatsUseCase,
) {

    @Operation(
        summary = "예약 가능한 날짜 목록 조회",
        description = "특정 콘서트의 예약 가능한 날짜 목록을 조회합니다.",
        operationId = "getAvailableSchedules",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = ConcertScheduleResponse::class)),
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
    @GetMapping("/{concertId}/schedules")
    fun getConcertSchedules(
        @Parameter(description = "콘서트 ID", required = true)
        @PathVariable concertId: Long,
    ): List<ConcertScheduleResponse> {
        val command = GetAvailableSchedulesCommand(concertId = concertId)
        val result = getAvailableSchedulesUseCase.execute(command)
        return result.schedules.map { schedule ->
            ConcertScheduleResponse(
                id = schedule.scheduleId,
                concertId = schedule.concertId,
                concertDate = schedule.concertDate.toString()
            )
        }
    }

    @Operation(
        summary = "예약 가능한 좌석 조회",
        description = """특정 날짜의 예약 가능한 좌석 정보를 조회합니다.
좌석 번호는 1-50번까지 관리됩니다.""",
        operationId = "getAvailableSeats",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = SeatResponse::class)),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "콘서트 또는 일정을 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{concertId}/schedules/{scheduleId}/seats")
    fun getConcertScheduleSeats(
        @Parameter(description = "콘서트 ID", required = true)
        @PathVariable concertId: Long,
        @Parameter(description = "콘서트 일정 ID", required = true)
        @PathVariable scheduleId: Long,
    ): List<SeatResponse> {
        val command = GetAvailableSeatsCommand(concertId = concertId, scheduleId = scheduleId)
        val result = getAvailableSeatsUseCase.execute(command)
        return result.seats.map { seat ->
            SeatResponse(
                id = seat.seatId,
                scheduleId = seat.concertScheduleId,
                seatNumber = seat.seatNumber,
                seatStatus = seat.status,
                price = seat.price
            )
        }
    }
}
