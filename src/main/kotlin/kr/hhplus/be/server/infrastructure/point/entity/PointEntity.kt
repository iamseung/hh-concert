package kr.hhplus.be.server.infrastructure.point.entity

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.point.model.Point
import kr.hhplus.be.server.infrastructure.comon.BaseEntity
import kr.hhplus.be.server.infrastructure.user.entity.UserEntity

@Entity
@Table(name = "point")
class PointEntity(
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val userEntity: UserEntity,

    // 포인트 잔액
    var balance: Int,
) : BaseEntity() {

    fun toDomain(): Point {
        return Point.reconstitute(
            id = id,
            userId = userEntity.id,
            balance = balance,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(point: Point) {
        this.balance = point.balance
    }

    companion object {
        fun fromDomain(
            point: Point,
            userEntity: UserEntity,
        ): PointEntity {
            return PointEntity(
                userEntity = userEntity,
                balance = point.balance,
            )
        }
    }
}
