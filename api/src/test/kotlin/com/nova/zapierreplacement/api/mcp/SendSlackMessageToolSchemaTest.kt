package com.nova.zapierreplacement.api.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.SlackSendResult
import com.nova.zapierreplacement.application.ports.WorkspaceContextPort
import com.nova.zapierreplacement.application.tools.messaging.SendSlackMessageUseCase
import com.nova.zapierreplacement.domain.workspace.WorkspaceId
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import kotlin.test.assertEquals

/**
 * Pins the input schema MCP clients see in `tools/list`. Clients use
 * `required` to decide what they must send, so an optional parameter
 * advertised as required forces them to invent a value.
 */
class SendSlackMessageToolSchemaTest {

    private val tool = SendSlackMessageMcpTool(
        useCase = SendSlackMessageUseCase(
            workspaceContext = object : WorkspaceContextPort {
                override fun current() = WorkspaceId("test")
            },
            slack = object : SlackMessagePort {
                override suspend fun postMessage(
                    workspace: WorkspaceId,
                    channel: String,
                    text: String,
                    threadTs: String?,
                ): SlackSendResult = SlackSendResult.Sent(channel, "ts")
            },
        ),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `send_slack_message requires channel and text but not threadTs`() {
        val definition = MethodToolCallbackProvider.builder()
            .toolObjects(tool)
            .build()
            .toolCallbacks
            .single { it.toolDefinition.name() == "send_slack_message" }
            .toolDefinition

        val schema = ObjectMapper().readTree(definition.inputSchema())
        val required = schema.path("required").map { it.asText() }.toSet()

        assertEquals(setOf("channel", "text"), required)
        assertEquals(
            setOf("channel", "text", "threadTs"),
            schema.path("properties").fieldNames().asSequence().toSet(),
        )
    }
}
