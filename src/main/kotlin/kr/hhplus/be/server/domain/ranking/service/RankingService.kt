package kr.hhplus.be.server.domain.ranking.service

import kr.hhplus.be.server.domain.ranking.model.RankingModel
import kr.hhplus.be.server.domain.ranking.repository.RankingRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 랭킹 도메인 서비스
 *
 * 콘서트 예약 랭킹 시스템의 핵심 비즈니스 로직을 담당합니다.
 */
@Service
class RankingService(
    private val rankingRepository: RankingRepository,
) {

    companion object {
        /**
         * Sliding window 크기 (분)
         * 최근 30분간의 판매량을 기준으로 랭킹을 산정합니다.
         */
        const val RANKING_WINDOW_MINUTES = 30

        /**
         * 랭킹에 표시할 최대 개수
         */
        const val TOP_RANKING_LIMIT = 10
    }

    /**
     * Top N 랭킹을 조회합니다.
     *
     * @param limit 조회할 랭킹 개수 (기본값: 10)
     * @return 랭킹 목록
     */
    fun getTopRankings(limit: Int = TOP_RANKING_LIMIT): List<RankingModel> {
        return rankingRepository.getTopRankings(limit)
    }

    /**
     * 예약 확정 시 랭킹을 실시간으로 업데이트합니다.
     *
     * 이벤트 기반 업데이트로 즉각적인 랭킹 반영을 보장합니다.
     *
     * @param concertId 콘서트 ID
     * @param concertTitle 콘서트 제목
     */
    fun onReservationConfirmed(concertId: Long, concertTitle: String) {
        val now = LocalDateTime.now()
        val timestamp = now.toInstant(ZoneOffset.UTC).toEpochMilli()

        // 1. 판매 이벤트 기록 (Sliding window용)
        rankingRepository.recordSale(concertId, timestamp)

        // 2. 랭킹 점수 증가 (실시간 반영)
        rankingRepository.incrementRankingScore(concertId, 1.0)

        // 3. 콘서트 메타정보 저장 (첫 판매 시)
        rankingRepository.saveConcertMetadata(concertId, concertTitle)
    }

    /**
     * 모든 콘서트의 랭킹을 배치로 재계산합니다.
     *
     * 주기적으로 실행되어 Redis와 실제 데이터를 동기화하고,
     * Sliding window 밖의 오래된 판매 이벤트를 정리합니다.
     */
    fun recalculateAllRankings() {
        val now = LocalDateTime.now()
        val cutoffTime = now.minusMinutes(RANKING_WINDOW_MINUTES.toLong())
        val cutoffTimestamp = cutoffTime.toInstant(ZoneOffset.UTC).toEpochMilli()

        val concertIds = rankingRepository.getAllConcertIds()

        concertIds.forEach { concertId ->
            // 1. 오래된 판매 이벤트 제거
            rankingRepository.removeOldSales(concertId, cutoffTimestamp)

            // 2. 정확한 판매량 재계산
            val actualSales = rankingRepository.calculateRecentSales(concertId, RANKING_WINDOW_MINUTES)

            // 3. 랭킹 점수 업데이트
            rankingRepository.updateRankingScore(concertId, actualSales.toDouble())
        }
    }

    /**
     * 특정 콘서트의 최근 N분간 판매량을 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @param windowMinutes Sliding window 크기 (분)
     * @return 최근 N분간 판매량
     */
    fun getRecentSales(concertId: Long, windowMinutes: Int = RANKING_WINDOW_MINUTES): Long {
        return rankingRepository.calculateRecentSales(concertId, windowMinutes)
    }
}
