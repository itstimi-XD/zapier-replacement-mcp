package com.nova.zapierreplacement.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    ],
)
class ZapierReplacementMcpApplicationTests {

    @Test
    fun contextLoads() {
        // smoke test — JPA/DataSource autoconfig disabled to avoid needing a live Postgres for context boot.
    }
}
