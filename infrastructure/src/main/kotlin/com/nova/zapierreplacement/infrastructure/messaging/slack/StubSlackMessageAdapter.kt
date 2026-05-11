package com.nova.zapierreplacement.infrastructure.messaging.slack

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.domain.workspace.WorkspaceId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Placeholder adapter that lets the MCP plumbing be verified end-to-end
 * before the real Slack Web API integration lands.
 *
 * The stub logs every call at INFO and returns
 * [SlackSendResult.Sent] with a synthetic timestamp. That is enough
 * for an MCP client (Claude Desktop, the MCP Inspector) to see the
 * tool register, accept arguments, and round-trip a structured
 * response — the contracts the next-pattern PR will exercise against
 * the live Slack Web API without changing any application code.
 *
 * Wiring: this adapter is registered unconditionally for now. When
 * the real adapter lands, swap selection to a Spring profile or a
 * config property; the application layer never has to change.
 */
@Component
class StubSlackMessageAdapter : SlackMessagePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun postMessage(
        workspace: WorkspaceId,
        channel: String,
        text: String,
        threadTs: String?,
    ): SlackSendResult {
        log.info(
            "[stub] slack.postMessage workspace={} channel={} threadTs={} textLength={}",
            workspace.value,
            channel,
            threadTs ?: "-",
            text.length,
        )
        return SlackSendResult.Sent(
            channel = channel,
            messageTs = "stub-${System.currentTimeMillis()}",
        )
    }
}

/**
 * Reserved property namespace for the upcoming live adapter — keeping
 * the binding registered (even though the stub ignores it) makes the
 * eventual switch a one-line config flip rather than a code change.
 *
 * Bound as a plain `@ConfigurationProperties` class (no `@Configuration`)
 * per Spring Boot's recommended style since 2.2; activated via
 * [SlackPropertiesActivation] below.
 */
@ConfigurationProperties(prefix = "twinface.slack")
class SlackProperties {
    var enabled: Boolean = false
    var baseUrl: String = "https://slack.com/api"
}

@Configuration
@EnableConfigurationProperties(SlackProperties::class)
class SlackPropertiesActivation
