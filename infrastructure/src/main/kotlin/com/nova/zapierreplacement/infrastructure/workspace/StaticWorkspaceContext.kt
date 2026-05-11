package com.nova.zapierreplacement.infrastructure.workspace

import com.nova.zapierreplacement.application.ports.WorkspaceContextPort
import com.nova.zapierreplacement.domain.workspace.WorkspaceId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Single-tenant adapter for the self-hosted reference deployment.
 *
 * The Twinface "Free (self-host)" tier is one instance per SMB — one
 * workspace, set at boot via `twinface.workspace.id`. This adapter
 * reads that value and returns the same [WorkspaceId] on every call,
 * which lets the use cases stay multi-tenant-shaped without a
 * persistence layer in v0.1.
 *
 * The multi-tenant adapter that reads workspace from MCP session / JWT
 * arrives when the hosted plan does.
 */
@Component
class StaticWorkspaceContext(
    // No `:default` fallback on purpose. If the property is missing in
    // production the context fails to start with a clear placeholder
    // error — far better than silently routing every tool call to a
    // workspace named "default" that nobody owns. The shipped
    // application.yml sets the property so dev / test still boot.
    @Value("\${twinface.workspace.id}")
    workspaceId: String,
) : WorkspaceContextPort {

    private val workspace = WorkspaceId(workspaceId)

    override fun current(): WorkspaceId = workspace
}
