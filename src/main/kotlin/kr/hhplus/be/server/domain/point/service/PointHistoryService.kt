package kr.hhplus.be.server.domain.point.service

import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.repository.PointHistoryRepository
import org.springframework.stereotype.Service

@Service
class PointHistoryService(
    private val pointHistoryRepository: PointHistoryRepository,
) {

    fun savePointHistory(userId: Long, amount: Int, transactionType: TransactionType) {
        pointHistoryRepository.save(userId, amount, transactionType)
    }
}
