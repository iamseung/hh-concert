package kr.hhplus.be.server.infrastructure.persistence.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.repository.PointHistoryRepository
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.infrastructure.persistence.point.entity.PointHistory
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import org.springframework.stereotype.Repository

@Repository
class PointHistoryRepositoryImpl(
    private val pointHistoryJpaRepository: PointHistoryJpaRepository,
) : PointHistoryRepository {

    override fun save(userModel: UserModel, amount: Int, transactionType: TransactionType): PointHistoryModel {
        val user = User.fromDomain(userModel)
        val pointHistory = PointHistory.of(user, amount, transactionType)
        val savedEntity = pointHistoryJpaRepository.save(pointHistory)
        return savedEntity.toModel()
    }
}
