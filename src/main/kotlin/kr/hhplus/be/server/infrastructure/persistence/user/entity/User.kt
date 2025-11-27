package kr.hhplus.be.server.infrastructure.persistence.user.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.user.model.UserModel
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "user")
class User(
    var userName: String,
    var email: String,
    var password: String,
) : BaseEntity() {

    fun toModel(): UserModel {
        return UserModel.reconstitute(
            id = id,
            userName = userName,
            email = email,
            password = password,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateFromDomain(userModel: UserModel) {
        this.userName = userModel.userName
        this.email = userModel.email
        this.password = userModel.password
    }

    companion object {
        fun fromDomain(userModel: UserModel): User {
            return User(
                userName = userModel.userName,
                email = userModel.email,
                password = userModel.password,
            )
        }
    }
}
