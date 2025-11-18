package kr.hhplus.be.server.domain.point.model

import kr.hhplus.be.server.domain.user.model.User
import java.time.LocalDateTime

data class PointHistory(
    val id: Long = 0,
    val user: User,
    val amount: Int,
    val transactionType: TransactionType,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isActive: Boolean = true,
    val isDeleted: Boolean = false
) {
    companion object {
        fun of(user: User, amount: Int, transactionType: TransactionType): PointHistory {
            return PointHistory(
                user = user,
                amount = amount,
                transactionType = transactionType
            )
        }
    }
}