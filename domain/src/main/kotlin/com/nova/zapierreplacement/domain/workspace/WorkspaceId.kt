package com.nova.zapierreplacement.domain.workspace

/**
 * Tenant identifier for the multi-tenant invariant called out in
 * [CLAUDE.md][../../../../../../../../CLAUDE.md] ("모든 tool에
 * workspace_id implicit 주입").
 *
 * Modeled as a value class so it cannot be silently mixed with other
 * `String`s (channel ids, message ids, tokens) anywhere in the codebase.
 * Adapters resolve it from their own context (HTTP header, MCP session
 * metadata, env var on a self-hosted instance) via
 * [com.nova.zapierreplacement.application.ports.WorkspaceContextPort].
 */
@JvmInline
value class WorkspaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkspaceId must not be blank." }
    }
}
