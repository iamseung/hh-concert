package kr.hhplus.be.server.infrastructure.persistence.point.entity

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.PointModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.persistence.user.entity.User

@Entity
@Table(name = "point")
class Point(
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    // 포인트 잔액
    var balance: Int,
) : BaseEntity() {

    fun toModel(): PointModel {
        return PointModel.reconstitute(
            id = id,
            userId = user.id,
            balance = balance,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(pointModel: PointModel) {
        this.balance = pointModel.balance
    }

    companion object {
        fun fromDomain(
            pointModel: PointModel,
            user: User,
        ): Point {
            return Point(
                user = user,
                balance = pointModel.balance,
            )
        }
    }
}
