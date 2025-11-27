package kr.hhplus.be.server.domain.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType

interface PointHistoryRepository {
    fun save(userId: Long, amount: Int, transactionType: TransactionType): PointHistoryModel
}
