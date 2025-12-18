package kr.hhplus.be.server.domain.ranking.repository

import kr.hhplus.be.server.domain.ranking.model.RankingModel

/**
 * 콘서트 랭킹 저장소 인터페이스
 *
 * Redis를 활용한 실시간 랭킹 시스템의 영속성 계층을 추상화합니다.
 */
interface RankingRepository {

    /**
     * 콘서트의 최근 판매 이벤트를 기록합니다.
     *
     * @param concertId 콘서트 ID
     * @param timestamp 판매 시각 (epoch millis)
     */
    fun recordSale(concertId: Long, timestamp: Long)

    /**
     * 콘서트의 랭킹 점수를 증가시킵니다.
     *
     * @param concertId 콘서트 ID
     * @param increment 증가할 점수 (기본값: 1.0)
     */
    fun incrementRankingScore(concertId: Long, increment: Double = 1.0)

    /**
     * Top N 랭킹을 조회합니다.
     *
     * @param limit 조회할 랭킹 개수 (기본값: 10)
     * @return 랭킹 목록
     */
    fun getTopRankings(limit: Int = 10): List<RankingModel>

    /**
     * 특정 시각 이전의 판매 이벤트를 제거합니다.
     *
     * @param concertId 콘서트 ID
     * @param cutoffTimestamp 기준 시각 (epoch millis)
     * @return 제거된 이벤트 개수
     */
    fun removeOldSales(concertId: Long, cutoffTimestamp: Long): Long

    /**
     * 콘서트의 최근 N분간 판매량을 계산합니다.
     *
     * @param concertId 콘서트 ID
     * @param windowMinutes Sliding window 크기 (분)
     * @return 최근 N분간 판매량
     */
    fun calculateRecentSales(concertId: Long, windowMinutes: Int): Long

    /**
     * 콘서트의 랭킹 점수를 정확한 값으로 재설정합니다.
     *
     * @param concertId 콘서트 ID
     * @param score 새로운 점수
     */
    fun updateRankingScore(concertId: Long, score: Double)

    /**
     * 모든 콘서트 ID를 조회합니다.
     *
     * @return 콘서트 ID 목록
     */
    fun getAllConcertIds(): List<Long>

    /**
     * 콘서트 메타정보를 저장합니다.
     *
     * @param concertId 콘서트 ID
     * @param title 콘서트 제목
     */
    fun saveConcertMetadata(concertId: Long, title: String)

    /**
     * 콘서트 제목을 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @return 콘서트 제목
     */
    fun getConcertTitle(concertId: Long): String?
}
