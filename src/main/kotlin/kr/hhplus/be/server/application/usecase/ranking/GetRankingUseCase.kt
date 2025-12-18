package kr.hhplus.be.server.application.usecase.ranking

import kr.hhplus.be.server.domain.ranking.service.RankingService
import org.springframework.stereotype.Component

/**
 * 콘서트 랭킹 조회 UseCase
 *
 * 최근 N분간 판매량 기준으로 콘서트 랭킹을 조회합니다.
 */
@Component
class GetRankingUseCase(
    private val rankingService: RankingService,
) {

    /**
     * Top N 랭킹을 조회합니다.
     *
     * @param command 랭킹 조회 요청
     * @return 랭킹 목록
     */
    fun execute(command: GetRankingCommand): GetRankingResult {
        val rankings = rankingService.getTopRankings(command.limit)

        val rankingDtos = rankings.map { ranking ->
            GetRankingResult.RankingDto(
                rank = ranking.rank,
                concertId = ranking.concertId,
                concertTitle = ranking.concertTitle,
                recentSales = ranking.recentSales,
            )
        }

        return GetRankingResult(rankings = rankingDtos)
    }
}
