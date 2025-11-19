package kr.hhplus.be.server.domain.point.model

import kr.hhplus.be.server.domain.user.model.UserModel
import java.time.LocalDateTime

data class PointHistoryModel(
    val id: Long = 0,
    val userModel: UserModel,
    val amount: Int,
    val transactionType: TransactionType,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun of(userModel: UserModel, amount: Int, transactionType: TransactionType): PointHistoryModel {
            return PointHistoryModel(
                userModel = userModel,
                amount = amount,
                transactionType = transactionType,
            )
        }
    }
}
