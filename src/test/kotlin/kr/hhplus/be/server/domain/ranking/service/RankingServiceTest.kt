package kr.hhplus.be.server.domain.ranking.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domain.ranking.model.RankingModel
import kr.hhplus.be.server.domain.ranking.repository.RankingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RankingServiceTest {

    private val rankingRepository: RankingRepository = mockk(relaxed = true)
    private val rankingService = RankingService(rankingRepository)

    @Test
    fun `예약 확정 시 랭킹이 업데이트된다`() {
        // given
        val concertId = 1L
        val concertTitle = "Test Concert"

        // when
        rankingService.onReservationConfirmed(concertId, concertTitle)

        // then
        verify(exactly = 1) {
            rankingRepository.recordSale(concertId, any())
            rankingRepository.incrementRankingScore(concertId, 1.0)
            rankingRepository.saveConcertMetadata(concertId, concertTitle)
        }
    }

    @Test
    fun `Top 10 랭킹을 조회한다`() {
        // given
        val expectedRankings = listOf(
            RankingModel(rank = 1, concertId = 1L, concertTitle = "Concert 1", recentSales = 100),
            RankingModel(rank = 2, concertId = 2L, concertTitle = "Concert 2", recentSales = 90),
        )
        every { rankingRepository.getTopRankings(10) } returns expectedRankings

        // when
        val result = rankingService.getTopRankings(10)

        // then
        assertEquals(2, result.size)
        assertEquals(1, result[0].rank)
        assertEquals(100, result[0].recentSales)
    }

    @Test
    fun `모든 콘서트의 랭킹을 재계산한다`() {
        // given
        val concertIds = listOf(1L, 2L, 3L)
        every { rankingRepository.getAllConcertIds() } returns concertIds
        every { rankingRepository.calculateRecentSales(any(), any()) } returns 10L

        // when
        rankingService.recalculateAllRankings()

        // then
        verify(exactly = 3) {
            rankingRepository.removeOldSales(any(), any())
            rankingRepository.calculateRecentSales(any(), 30)
            rankingRepository.updateRankingScore(any(), any())
        }
    }
}
