package kr.hhplus.be.server.domain.point.repository

import kr.hhplus.be.server.domain.point.model.PointHistoryModel
import kr.hhplus.be.server.domain.point.model.TransactionType
import kr.hhplus.be.server.domain.user.model.UserModel

interface PointHistoryRepository {
    fun save(userModel: UserModel, amount: Int, transactionType: TransactionType): PointHistoryModel
}
