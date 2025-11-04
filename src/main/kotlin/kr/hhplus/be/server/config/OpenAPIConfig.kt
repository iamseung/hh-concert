package kr.hhplus.be.server.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

/**
 * OpenAPI/Swagger Configuration
 *
 * docs/openapi.yml 파일을 사용하여 API 명세를 정의합니다.
 * Swagger UI는 http://localhost:8080/swagger-ui.html 에서 확인 가능합니다.
 */
@Configuration
class OpenAPIConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        // docs/openapi.yml 파일 로드
        val openApiYmlPath = "static/docs/openapi.yml"

        return try {
            // ClassPath에서 openapi.yml 파일 읽기
            val resource = ClassPathResource(openApiYmlPath)

            if (resource.exists()) {
                // OpenAPI Parser로 yml 파일 파싱
                val parseResult = OpenAPIV3Parser().read(resource.file.absolutePath)
                parseResult ?: createDefaultOpenAPI()
            } else {
                createDefaultOpenAPI()
            }
        } catch (e: Exception) {
            println("Warning: Could not load custom OpenAPI spec from $openApiYmlPath")
            println("Error: ${e.message}")
            createDefaultOpenAPI()
        }
    }

    /**
     * openapi.yml 로드 실패 시 기본 OpenAPI 객체 반환
     */
    private fun createDefaultOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                io.swagger.v3.oas.models.info.Info()
                    .title("Kotlin Server API")
                    .description("API 명세는 docs/openapi.yml 파일에서 관리됩니다")
                    .version("1.0.0")
            )
    }
}