package com.nova.zapierreplacement.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Boots the context with the shipped application.yml and no overrides,
 * so this test fails whenever plain `./gradlew :api:bootRun` would
 * (e.g. an autoconfiguration that needs a database nobody provides).
 */
@SpringBootTest
class ZapierReplacementMcpApplicationTests {

    @Test
    fun contextLoads() {
    }
}
