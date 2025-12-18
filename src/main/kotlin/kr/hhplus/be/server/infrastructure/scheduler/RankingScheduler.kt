package kr.hhplus.be.server.infrastructure.scheduler

import kr.hhplus.be.server.domain.ranking.service.RankingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 랭킹 스케줄러
 *
 * 주기적으로 랭킹을 재계산하여 Redis 데이터를 정리하고 정확성을 보장합니다.
 */
@Component
class RankingScheduler(
    private val rankingService: RankingService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 랭킹 재계산 배치 작업
     *
     * - 실행 주기: 매 1분마다
     * - 작업 내용:
     *   1. 오래된 판매 이벤트 제거 (30분 이전)
     *   2. 정확한 판매량 재계산
     *   3. 랭킹 점수 업데이트
     *
     * 하이브리드 전략:
     * - 실시간: 예약 확정 시 즉시 랭킹 증가
     * - 배치: 주기적으로 Sliding Window 정리 및 동기화
     */
    @Scheduled(cron = "0 * * * * *") // 매 1분마다 실행
    fun recalculateRankings() {
        try {
            logger.info("Starting ranking recalculation batch job")

            rankingService.recalculateAllRankings()

            logger.info("Ranking recalculation batch job completed successfully")
        } catch (e: Exception) {
            logger.error("Failed to recalculate rankings: {}", e.message, e)
        }
    }
}
