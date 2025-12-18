package kr.hhplus.be.server.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kr.hhplus.be.server.application.usecase.ranking.GetRankingCommand
import kr.hhplus.be.server.application.usecase.ranking.GetRankingResult
import kr.hhplus.be.server.application.usecase.ranking.GetRankingUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rankings")
@Tag(name = "Ranking", description = "콘서트 랭킹")
class RankingController(
    private val getRankingUseCase: GetRankingUseCase,
) {

    @GetMapping
    @Operation(
        summary = "콘서트 빠른 매진 랭킹 조회",
        description = "최근 30분간 판매량 기준으로 빠른 매진 콘서트 랭킹을 조회합니다.",
    )
    fun getRankings(
        @RequestParam(defaultValue = "10") limit: Int,
    ): GetRankingResult {
        val command = GetRankingCommand(limit = limit)
        return getRankingUseCase.execute(command)
    }
}
