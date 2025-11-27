package kr.hhplus.be.server.infrastructure.persistence.point.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "point")
class Point(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    // 포인트 잔액
    var balance: Int,
) : BaseEntity() {

    fun toModel(): PointModel {
        return PointModel.reconstitute(
            id = id,
            userId = userId,
            balance = balance,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(pointModel: PointModel) {
        this.balance = pointModel.balance
    }

    companion object {
        fun fromDomain(pointModel: PointModel): Point {
            return Point(
                userId = pointModel.userId,
                balance = pointModel.balance,
            )
        }
    }
}
