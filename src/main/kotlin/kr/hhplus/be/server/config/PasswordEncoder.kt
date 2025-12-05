package kr.hhplus.be.server.config

interface PasswordEncoder {
    fun encode(password: String): String
    fun matches(rawPassword: String, encodedPassword: String): Boolean
}