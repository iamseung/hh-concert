package kr.hhplus.be.server.api.config

import kr.hhplus.be.server.api.config.interceptor.QueueTokenInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC 설정
 */
@Configuration
class WebConfig(
    private val queueTokenInterceptor: QueueTokenInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns(
                "/api/v1/concerts/*/reservations", // 좌석 예약
            )
            .excludePathPatterns(
                "/api/v1/concerts/*/reservations", // 예약 조회는 제외 (GET)
            )
    }
}
