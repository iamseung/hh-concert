package kr.hhplus.be.server.support

import kr.hhplus.be.server.config.TestSecurityConfig
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig::class)
abstract class AbstractIntegrationContainerBaseTest
