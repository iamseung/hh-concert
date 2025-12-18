package kr.hhplus.be.server.application.usecase.ranking

/**
 * 랭킹 조회 결과
 *
 * @property rankings 랭킹 목록
 */
data class GetRankingResult(
    val rankings: List<RankingDto>,
) {
    /**
     * 랭킹 정보 DTO
     *
     * @property rank 순위
     * @property concertId 콘서트 ID
     * @property concertTitle 콘서트 제목
     * @property recentSales 최근 30분간 판매량
     */
    data class RankingDto(
        val rank: Int,
        val concertId: Long,
        val concertTitle: String,
        val recentSales: Long,
    )
}
