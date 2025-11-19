package kr.hhplus.be.server.domain.point.service

import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.repository.PointHistoryRepository
import kr.hhplus.be.server.domain.user.model.UserModel
import org.springframework.stereotype.Service

@Service
class PointHistoryService(
    private val pointHistoryRepository: PointHistoryRepository,
) {

    fun savePointHistory(userModel: UserModel, amount: Int, transactionType: TransactionType) {
        pointHistoryRepository.save(userModel, amount, transactionType)
    }
}
