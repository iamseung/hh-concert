package kr.hhplus.be.server.infrastructure.user.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.user.model.User
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "user")
class UserEntity(
    var userName: String,
    var email: String,
    var password: String,
) : BaseEntity() {

    fun toDomain(): User {
        return User.reconstitute(
            id = id,
            userName = userName,
            email = email,
            password = password,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(user: User) {
        this.userName = user.userName
        this.email = user.email
        this.password = user.password
    }

    companion object {
        fun fromDomain(user: User): UserEntity {
            return UserEntity(
                userName = user.userName,
                email = user.email,
                password = user.password,
            )
        }
    }
}
