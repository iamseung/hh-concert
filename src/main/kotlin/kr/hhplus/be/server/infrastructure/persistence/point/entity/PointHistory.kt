package kr.hhplus.be.server.infrastructure.persistence.point.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User

@Entity
@Table(name = "point_history")
class PointHistory(
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    val amount: Int,

    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType,
) : BaseEntity() {

    fun toModel(): PointHistoryModel {
        return PointHistoryModel(
            id = id,
            userModel = user.toModel(),
            amount = amount,
            transactionType = transactionType,
            createdAt = createdAt,
        )
    }

    companion object {
        fun of(user: User, amount: Int, transactionType: TransactionType): PointHistory {
            return PointHistory(
                user = user,
                amount = amount,
                transactionType = transactionType,
            )
        }

        fun fromDomain(
            pointHistoryModel: kr.hhplus.be.server.domain.point.model.PointHistoryModel,
            user: User,
        ): PointHistory {
            return PointHistory(
                user = user,
                amount = pointHistoryModel.amount,
                transactionType = pointHistoryModel.transactionType,
            )
        }
    }
}
