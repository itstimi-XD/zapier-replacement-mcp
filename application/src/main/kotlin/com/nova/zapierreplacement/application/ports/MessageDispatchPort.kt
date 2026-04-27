package com.nova.zapierreplacement.application.ports

import com.nova.zapierreplacement.domain.workflow.NotificationChannel

/**
 * Outbound port for dispatching a message to an external messaging system.
 *
 * Implementations live in the :infrastructure module (TwilioAdapter,
 * GmailAdapter, SlackAdapter, etc.). The application layer must NEVER
 * import an SDK directly — always through this interface.
 */
interface MessageDispatchPort {
    suspend fun dispatch(
        channel: NotificationChannel,
        message: DispatchMessage,
    ): DispatchResult
}

data class DispatchMessage(
    val recipient: String,
    val body: String,
    val metadata: Map<String, Any?> = emptyMap(),
)

sealed interface DispatchResult {
    data class Sent(val externalId: String) : DispatchResult
    data class Failed(val reason: String) : DispatchResult
}
