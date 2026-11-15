package com.nova.zapierreplacement.api

import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * Full application context (not just the infrastructure slice) with
 * Slack enabled: startup must fail, and the actionable text must be
 * in the cause chain even though the first bean to need the port is
 * the use case wired in :api.
 */
class SlackEnabledStartupTest {

    @Test
    fun `application refuses to start when twinface slack enabled is true`() {
        val failure = assertFailsWith<Throwable> {
            SpringApplicationBuilder(ZapierReplacementMcpApplication::class.java)
                // Command-line args, not .properties(): builder "default
                // properties" rank below application.yml and would lose
                // to its `enabled: false`.
                .run("--twinface.slack.enabled=true", "--server.port=0")
                .close()
        }
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertContains(messages, "live Slack adapter is not implemented yet")
        assertContains(messages, "twinface.slack.enabled=false")
    }
}
