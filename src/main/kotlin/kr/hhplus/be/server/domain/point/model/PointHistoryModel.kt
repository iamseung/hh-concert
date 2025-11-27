package kr.hhplus.be.server.domain.point.model

import java.time.LocalDateTime

data class PointHistoryModel(
    val id: Long = 0,
    val userId: Long,
    val amount: Int,
    val transactionType: TransactionType,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun of(userId: Long, amount: Int, transactionType: TransactionType): PointHistoryModel {
            return PointHistoryModel(
                userId = userId,
                amount = amount,
                transactionType = transactionType,
            )
        }
    }
}
