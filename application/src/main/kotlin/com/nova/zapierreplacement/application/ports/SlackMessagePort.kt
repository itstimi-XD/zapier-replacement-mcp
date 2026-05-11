package com.nova.zapierreplacement.application.ports

import com.nova.zapierreplacement.domain.workspace.WorkspaceId

/**
 * Outbound port for posting a message to Slack on behalf of a workspace.
 *
 * The adapter is responsible for looking up the workspace's Slack bot
 * token (from its own store), making the HTTP call, and translating
 * the Slack-specific response into a [SlackSendResult]. The application
 * layer never imports the Slack SDK directly — only this interface.
 *
 * Why a port: the v0.1 [com.nova.zapierreplacement.application.tools.messaging.SendSlackMessageUseCase]
 * ships with a stub adapter so the MCP plumbing can be verified
 * end-to-end before the real Slack Web API integration lands. Both
 * adapters implement the same port; swapping them is a Spring bean
 * configuration concern.
 */
interface SlackMessagePort {
    suspend fun postMessage(
        workspace: WorkspaceId,
        channel: String,
        text: String,
        threadTs: String? = null,
    ): SlackSendResult
}

sealed interface SlackSendResult {

    /** Slack accepted the message. [messageTs] is the timestamp id returned by Slack. */
    data class Sent(val channel: String, val messageTs: String) : SlackSendResult

    /** Slack rejected or the call failed before reaching Slack. */
    data class Failed(val reason: String) : SlackSendResult
}
