package kr.hhplus.be.server.config

import jakarta.servlet.http.HttpServletResponse
import kr.hhplus.be.server.domain.auth.JwtTokenProvider
import kr.hhplus.be.server.infrastructure.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@Profile("!test")
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/users/*",
                        "/api/v1/concerts/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .exceptionHandling {
                it.authenticationEntryPoint(jwtAuthenticationEntryPoint())
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:8080",
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtAuthenticationEntryPoint(): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { request, response, _ ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(
                """
                {
                    "message": "인증이 필요합니다",
                    "path": "${request.requestURI}"
                }
                """.trimIndent(),
            )
        }
    }
}
