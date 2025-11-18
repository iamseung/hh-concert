package kr.hhplus.be.server.infrastructure.user.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.hhplus.be.server.infrastructure.comon.BaseEntity

@Entity
@Table(name = "user")
class UserEntity(
    val userName: String,
    val email: String,
    val password: String,
) : BaseEntity()
