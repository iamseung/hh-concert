package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.api.dto.response.ConcertResponse
import kr.hhplus.be.server.api.dto.response.ConcertScheduleResponse
import kr.hhplus.be.server.api.dto.response.SeatResponse
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSchedulesCommand
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSchedulesUseCase
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsCommand
import kr.hhplus.be.server.application.usecase.concert.GetAvailableSeatsUseCase
import kr.hhplus.be.server.application.usecase.concert.GetConcertsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/concerts")
@Tag(name = "Concerts API", description = "콘서트 조회")
class ConcertController(
    private val getConcertsUseCase: GetConcertsUseCase,
    private val getAvailableSchedulesUseCase: GetAvailableSchedulesUseCase,
    private val getAvailableSeatsUseCase: GetAvailableSeatsUseCase,
) {

    @Operation(
        summary = "콘서트 목록 조회",
        description = "전체 콘서트 목록을 조회합니다.",
        operationId = "getConcerts",
    )
    @GetMapping
    fun getConcerts(): List<ConcertResponse> {
        val result = getConcertsUseCase.execute()
        return result.concerts.map { concert ->
            ConcertResponse(
                id = concert.concertId,
                title = concert.title,
                description = concert.description,
            )
        }
    }

    @Operation(
        summary = "예약 가능한 날짜 목록 조회",
        description = "특정 콘서트의 예약 가능한 날짜 목록을 조회합니다.",
        operationId = "getAvailableSchedules",
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
                concertDate = schedule.concertDate.toString(), // LocalDateTime이 ISO-8601 포맷으로 변환됨
            )
        }
    }

    @Operation(
        summary = "예약 가능한 좌석 조회",
        description = """특정 날짜의 예약 가능한 좌석 정보를 조회합니다.
좌석 번호는 1-50번까지 관리됩니다.""",
        operationId = "getAvailableSeats",
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
                price = seat.price,
            )
        }
    }
}
