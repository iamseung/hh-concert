package kr.hhplus.be.server.domain.point.model

import kr.hhplus.be.server.common.exception.BusinessException
import kr.hhplus.be.server.common.exception.ErrorCode
import java.time.LocalDateTime

class PointModel private constructor(
    var id: Long,
    val userId: Long,
    var balance: Int,
    val createdAt: LocalDateTime,
    var updatedAt: LocalDateTime,
) {

    fun chargePoint(amount: Int) {
        validatePositiveAmount(amount)
        this.balance += amount
        this.updatedAt = LocalDateTime.now()
    }

    fun usePoint(amount: Int) {
        validatePositiveAmount(amount)
        validateSufficientPoint(amount)
        this.balance -= amount
        this.updatedAt = LocalDateTime.now()
    }

    private fun validatePositiveAmount(amount: Int) {
        if (amount <= 0) {
            throw BusinessException(ErrorCode.INVALID_CHARGE_AMOUNT)
        }
    }

    private fun validateSufficientPoint(amount: Int) {
        if (balance < amount) {
            throw BusinessException(ErrorCode.INSUFFICIENT_POINTS)
        }
    }

    companion object {
        fun create(userId: Long, balance: Int = 0): PointModel {
            val now = LocalDateTime.now()
            return PointModel(
                id = 0L,
                userId = userId,
                balance = balance,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            userId: Long,
            balance: Int,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
        ): PointModel {
            return PointModel(
                id = id,
                userId = userId,
                balance = balance,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
