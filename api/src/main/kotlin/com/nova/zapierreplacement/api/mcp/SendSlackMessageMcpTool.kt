package com.nova.zapierreplacement.api.mcp

import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.application.tools.messaging.SendSlackMessageUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * MCP wrapper around [SendSlackMessageUseCase]. The Spring AI MCP
 * starter discovers methods annotated with `@Tool` on registered
 * tool-object beans (see [McpToolsConfiguration]) and exposes them
 * through the MCP server endpoint.
 *
 * Why this class is in `:api` and not in `:application`:
 * - The `@Tool` / `@ToolParam` annotations are Spring AI types.
 *   Application layer is framework-free by ArchUnit rule, so the MCP
 *   surface must live in the api layer.
 * - If MCP gets replaced by a different protocol someday, only this
 *   thin wrapper changes — the use case is independent.
 *
 * Why `runBlocking(mcpToolDispatcher)`:
 * - The use case is `suspend` (idiomatic Kotlin for async I/O), but
 *   Spring AI's `@Tool` infrastructure currently dispatches on a
 *   plain Tomcat worker thread. A naked `runBlocking` would pin that
 *   worker until the suspended call completes, exhausting Tomcat's
 *   pool under concurrent MCP invocations. Routing the coroutine to
 *   a dedicated dispatcher (`mcpToolDispatcher`, see
 *   [McpToolsConfiguration]) keeps the Tomcat threads free to accept
 *   the next request while the I/O runs on a thread pool sized for
 *   blocking work.
 * - When Spring AI ships coroutine-aware dispatch this collapses to a
 *   direct call and the dispatcher bean retires.
 */
@Component
class SendSlackMessageMcpTool(
    private val useCase: SendSlackMessageUseCase,
    @Qualifier("mcpToolDispatcher")
    private val dispatcher: CoroutineDispatcher,
) {

    @Tool(
        name = "send_slack_message",
        description = """
            Post a message to a Slack channel in your workspace.
            Use when you need to notify a human team about an event,
            a status change, or the result of a workflow.
        """,
    )
    fun sendSlackMessage(
        @ToolParam(description = "Slack channel — '#general', 'C1234567', or '@username'.")
        channel: String,

        @ToolParam(description = "Message text. Supports Slack mrkdwn (bold, links, mentions).")
        text: String,

        // Nullability handles required=false on its own; the annotation
        // is here purely to attach the description for the MCP client.
        @ToolParam(description = "Optional thread timestamp to reply inside a thread. Omit for a top-level message.")
        threadTs: String? = null,
    ): McpResult = runBlocking(dispatcher) {
        val outcome = useCase.execute(channel = channel, text = text, threadTs = threadTs)
        when (outcome) {
            is SlackSendResult.Sent -> McpResult(
                ok = true,
                channel = outcome.channel,
                messageTs = outcome.messageTs,
                error = null,
            )
            is SlackSendResult.Failed -> McpResult(
                ok = false,
                channel = null,
                messageTs = null,
                error = outcome.reason,
            )
        }
    }

    /**
     * Flat shape returned across the MCP wire. Kept as a plain data
     * class (not the sealed [SlackSendResult]) so the JSON shape stays
     * a single object — MCP clients deserialize this most predictably.
     */
    data class McpResult(
        val ok: Boolean,
        val channel: String?,
        val messageTs: String?,
        val error: String?,
    )
}
