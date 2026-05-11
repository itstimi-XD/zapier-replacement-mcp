package com.nova.zapierreplacement.application.tools.messaging

import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.application.ports.WorkspaceContextPort

/**
 * The application-side logic behind the first MCP tool, `send_slack_message`.
 *
 * The MCP tool wrapper (in `:api`) is intentionally a thin shell that
 * just forwards arguments here, so the same logic can also be invoked
 * from a non-MCP entry point later (HTTP webhook, scheduled job, etc.)
 * without duplicating validation or workspace resolution.
 *
 * Responsibilities:
 * - Validate the inputs the MCP layer is too generic to validate
 *   (channel non-blank, text length range).
 * - Resolve the current workspace via [WorkspaceContextPort] so the
 *   tool signature stays free of `workspace_id` — that's the
 *   "implicit workspace" CLAUDE.md calls for.
 * - Delegate the actual send to the [SlackMessagePort] adapter.
 *
 * Failures from the adapter are surfaced as [SlackSendResult.Failed];
 * thrown exceptions from `postMessage` are NOT caught here because the
 * adapter's contract is to translate them into [SlackSendResult.Failed]
 * itself.
 */
class SendSlackMessageUseCase(
    private val workspaceContext: WorkspaceContextPort,
    private val slack: SlackMessagePort,
) {

    suspend fun execute(
        channel: String,
        text: String,
        threadTs: String? = null,
    ): SlackSendResult {
        require(channel.isNotBlank()) { "channel must not be blank" }
        require(text.length in 1..40_000) { "text length must be 1..40000 chars (got ${text.length})" }

        val workspace = workspaceContext.current()
        return slack.postMessage(
            workspace = workspace,
            channel = channel,
            text = text,
            threadTs = threadTs,
        )
    }
}
