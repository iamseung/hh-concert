package kr.hhplus.be.server.domain.ranking.model

/**
 * 콘서트 랭킹 도메인 모델
 *
 * Redis Sorted Set에서 조회한 랭킹 정보를 표현합니다.
 *
 * @property rank 순위 (1부터 시작)
 * @property concertId 콘서트 ID
 * @property concertTitle 콘서트 제목
 * @property recentSales 최근 N분간 판매량
 */
data class RankingModel(
    val rank: Int,
    val concertId: Long,
    val concertTitle: String,
    val recentSales: Long,
) {
    companion object {
        /**
         * Redis 조회 결과로부터 RankingModel을 생성합니다.
         *
         * @param rank 순위 (0부터 시작하는 인덱스)
         * @param concertId 콘서트 ID
         * @param concertTitle 콘서트 제목
         * @param score Redis Sorted Set의 score (판매량)
         * @return RankingModel 인스턴스
         */
        fun from(
            rank: Long,
            concertId: Long,
            concertTitle: String,
            score: Double,
        ): RankingModel {
            return RankingModel(
                rank = (rank + 1).toInt(), // 0-based index → 1-based rank
                concertId = concertId,
                concertTitle = concertTitle,
                recentSales = score.toLong(),
            )
        }
    }
}
