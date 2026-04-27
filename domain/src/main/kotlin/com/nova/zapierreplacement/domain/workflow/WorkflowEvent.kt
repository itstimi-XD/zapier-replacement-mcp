package com.nova.zapierreplacement.domain.workflow

/**
 * The trigger that enters the routing system — typically a webhook payload,
 * a row insert, or any other "something happened" signal.
 *
 * Kept generic on purpose: callers project their adapter-specific payload
 * into a flat map so the routing rules stay decoupled from the source.
 */
data class WorkflowEvent(
    val id: String,
    val type: String,
    val payload: Map<String, Any?>,
)
