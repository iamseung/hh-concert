package kr.hhplus.be.server.infrastructure.point.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity

@Entity
@Table(name = "point_history")
class PointHistoryEntity(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val userEntity: UserEntity,

    val amount: Int,

    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType,
) : BaseEntity() {

    fun toDomain(): kr.hhplus.be.server.domain.point.model.PointHistory {
        return kr.hhplus.be.server.domain.point.model.PointHistory(
            id = id,
            user = userEntity.toDomain(),
            amount = amount,
            transactionType = transactionType,
            createdAt = createdAt,
        )
    }

    companion object {
        fun of(userEntity: UserEntity, amount: Int, transactionType: TransactionType): PointHistoryEntity {
            return PointHistoryEntity(
                userEntity = userEntity,
                amount = amount,
                transactionType = transactionType,
            )
        }

        fun fromDomain(
            pointHistory: kr.hhplus.be.server.domain.point.model.PointHistory,
            userEntity: UserEntity,
        ): PointHistoryEntity {
            return PointHistoryEntity(
                userEntity = userEntity,
                amount = pointHistory.amount,
                transactionType = pointHistory.transactionType,
            )
        }
    }
}
