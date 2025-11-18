package kr.hhplus.be.server.infrastructure.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistory
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.point.repository.PointHistoryRepository
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.infrastructure.point.entity.PointHistoryEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity
import org.springframework.stereotype.Repository

@Repository
class PointHistoryRepositoryImpl(
    private val pointHistoryJpaRepository: PointHistoryJpaRepository,
) : PointHistoryRepository {

    override fun save(user: User, amount: Int, transactionType: TransactionType): PointHistory {
        val userEntity = UserEntity.from(user)
        val pointHistoryEntity = PointHistoryEntity.of(userEntity, amount, transactionType)
        val savedEntity = pointHistoryJpaRepository.save(pointHistoryEntity)

        return PointHistory(
            id = savedEntity.id,
            user = user,
            amount = savedEntity.amount,
            transactionType = savedEntity.transactionType,
            createdAt = savedEntity.createdAt,
            isActive = savedEntity.isActive,
            isDeleted = savedEntity.isDeleted
        )
    }
}
