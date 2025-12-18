package kr.hhplus.be.server.application.usecase.ranking

/**
 * 랭킹 조회 요청 커맨드
 *
 * @property limit 조회할 랭킹 개수 (기본값: 10)
 */
data class GetRankingCommand(
    val limit: Int = 10,
)
