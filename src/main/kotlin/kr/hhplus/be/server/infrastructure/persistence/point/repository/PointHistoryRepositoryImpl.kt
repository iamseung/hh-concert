package kr.hhplus.be.server.infrastructure.persistence.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.repository.PointHistoryRepository
import kr.hhplus.be.server.infrastructure.persistence.point.entity.PointHistory
import org.springframework.stereotype.Repository

@Repository
class PointHistoryRepositoryImpl(
    private val pointHistoryJpaRepository: PointHistoryJpaRepository,
) : PointHistoryRepository {

    override fun save(userId: Long, amount: Int, transactionType: TransactionType): PointHistoryModel {
        val pointHistoryModel = PointHistoryModel.of(userId, amount, transactionType)
        val pointHistory = PointHistory.fromDomain(pointHistoryModel)
        val savedEntity = pointHistoryJpaRepository.save(pointHistory)
        return savedEntity.toModel()
    }
}
