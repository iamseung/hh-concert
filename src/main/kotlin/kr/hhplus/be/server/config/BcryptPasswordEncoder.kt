package kr.hhplus.be.server.config

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BcryptPasswordEncoder : PasswordEncoder {

    private val bcryptEncoder = BCryptPasswordEncoder(12)

    override fun encode(password: String): String {
        return bcryptEncoder.encode(password)
    }

    override fun matches(rawPassword: String, encodedPassword: String): Boolean {
        return bcryptEncoder.matches(rawPassword, encodedPassword)
    }
}
