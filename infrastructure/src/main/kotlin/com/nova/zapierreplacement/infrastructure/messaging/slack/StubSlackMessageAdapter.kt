package com.nova.zapierreplacement.infrastructure.messaging.slack

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.domain.workspace.WorkspaceId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
 * Wiring: registered only while `twinface.slack.enabled` is `false` or
 * unset. With `enabled=true` the stub steps aside and
 * [SlackConfiguration] fails startup instead, so the stub can never
 * report success for a message the operator expected to reach Slack.
 */
@Component
@ConditionalOnProperty(
    prefix = "twinface.slack",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
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
