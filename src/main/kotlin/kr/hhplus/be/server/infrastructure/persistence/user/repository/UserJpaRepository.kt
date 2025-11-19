package kr.hhplus.be.server.infrastructure.persistence.user.repository

import kr.hhplus.be.server.infrastructure.persistence.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
