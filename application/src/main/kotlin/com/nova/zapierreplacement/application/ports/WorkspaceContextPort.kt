package com.nova.zapierreplacement.application.ports

import com.nova.zapierreplacement.domain.workspace.WorkspaceId

/**
 * Outbound port for resolving the workspace the current call is acting on.
 *
 * In the self-hosted reference deployment (one Twinface instance per
 * SMB), a static implementation that returns a configured id is the
 * right shape. In a hosted multi-tenant deployment, the adapter reads
 * the workspace from an HTTP header / MCP session / JWT claim.
 *
 * The use cases never see how the answer was derived — they ask
 * [current] and proceed. Keeping this behind a port is what lets the
 * deployment story change without touching application logic.
 */
interface WorkspaceContextPort {
    fun current(): WorkspaceId
}
