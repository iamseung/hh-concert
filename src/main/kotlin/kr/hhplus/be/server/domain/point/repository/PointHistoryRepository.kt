package kr.hhplus.be.server.domain.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistory
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.user.model.User

interface PointHistoryRepository {
    fun save(user: User, amount: Int, transactionType: TransactionType): PointHistory
}
