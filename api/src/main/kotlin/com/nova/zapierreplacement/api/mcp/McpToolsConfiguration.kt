package com.nova.zapierreplacement.api.mcp

import com.nova.zapierreplacement.application.tools.messaging.SendSlackMessageUseCase
import com.nova.zapierreplacement.application.ports.SlackMessagePort
import com.nova.zapierreplacement.application.ports.WorkspaceContextPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the MCP tool surface into the Spring AI MCP server starter.
 *
 * Three beans live here:
 * - [SendSlackMessageUseCase] — instantiated explicitly rather than
 *   `@Component`-scanned because the use case is in `:application`,
 *   which by ArchUnit rule cannot depend on Spring. Constructing it
 *   here keeps the application layer pure while still letting Spring
 *   inject the adapters.
 * - `mcpToolDispatcher` — the [CoroutineDispatcher] every MCP tool
 *   wrapper uses to host its `runBlocking` bridge. Backed by
 *   [Dispatchers.IO] so the suspended I/O does not pin Tomcat
 *   workers; swap to `Dispatchers.Default` for CPU-bound tools or to
 *   a virtual-thread executor when the JDK and the rest of the stack
 *   permit. Retires when Spring AI ships coroutine-aware dispatch.
 * - [ToolCallbackProvider] — Spring AI reads this to discover every
 *   `@Tool`-annotated method on the listed objects. As new tools land,
 *   add their wrapper class to the `toolObjects(...)` call here.
 */
@Configuration
class McpToolsConfiguration {

    @Bean
    fun sendSlackMessageUseCase(
        workspaceContext: WorkspaceContextPort,
        slack: SlackMessagePort,
    ): SendSlackMessageUseCase = SendSlackMessageUseCase(
        workspaceContext = workspaceContext,
        slack = slack,
    )

    @Bean(name = ["mcpToolDispatcher"])
    fun mcpToolDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Bean
    fun twinfaceMcpTools(
        sendSlackMessage: SendSlackMessageMcpTool,
    ): ToolCallbackProvider = MethodToolCallbackProvider.builder()
        .toolObjects(sendSlackMessage)
        .build()
}
