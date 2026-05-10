package com.nova.zapierreplacement.domain.workflow

/**
 * An incoming webhook delivery from an external source.
 *
 * Distinct from [WorkflowEvent] on purpose: this type carries provenance
 * (which provider sent it) and arrival time, both of which the polling-to-
 * webhook anti-pattern needs to model. Subscription matching is keyed on
 * [source] and [eventType], and idempotency is keyed on a value extracted
 * from [body] — providers retry on 5xx and re-deliver on network blips,
 * so dedup is part of the pattern, not optional.
 */
data class WebhookEvent(
    val source: String,
    val eventType: String,
    val body: Map<String, Any?>,
    val receivedAtEpochMillis: Long,
)
