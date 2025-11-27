package kr.hhplus.be.server.infrastructure.persistence.point.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "point_history")
class PointHistory(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    val amount: Int,

    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType,
) : BaseEntity() {

    fun toModel(): PointHistoryModel {
        return PointHistoryModel(
            id = id,
            userId = userId,
            amount = amount,
            transactionType = transactionType,
            createdAt = createdAt,
        )
    }

    companion object {
        fun fromDomain(pointHistoryModel: PointHistoryModel): PointHistory {
            return PointHistory(
                userId = pointHistoryModel.userId,
                amount = pointHistoryModel.amount,
                transactionType = pointHistoryModel.transactionType,
            )
        }
    }
}
