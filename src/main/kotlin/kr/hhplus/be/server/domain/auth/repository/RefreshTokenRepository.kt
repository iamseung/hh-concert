package kr.hhplus.be.server.domain.auth.repository

import java.time.Duration

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String, expiration: Duration)
    fun findByUserId(userId: Long): String?
    fun delete(userId: Long)
}
