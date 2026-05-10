package com.nova.zapierreplacement.domain.workflow

/**
 * The identity and inbound envelope of an asynchronously-processed request.
 *
 * Companion to [AsyncRequestState]. The pattern this models — accept
 * the inbound webhook, return immediately with this id, do the actual
 * work in the background, expose the result via callback or polling —
 * replaces Zapier's synchronous webhook chain. Sync chains compound
 * latency across every hop and burn one task per hop; async fan-out
 * with an explicit handle returns the original sender in milliseconds
 * and lets the work happen on its own clock.
 *
 * - [id] must be a collision-resistant random identifier (UUID v4/v7
 *   or equivalent). The registry rejects collisions, so a clock-based
 *   id will fail under concurrent acceptance — choose the generator
 *   accordingly when wiring [AcceptAsyncRequestUseCase].
 * - [acceptedAtEpochMillis] is the deterministic timestamp the use
 *   case stamps when it persists the initial
 *   [AsyncRequestState.Accepted]. Keep this in the domain (not just
 *   on infrastructure rows) so audit queries and SLO calculations
 *   don't have to round-trip through the adapter.
 * - [payload] is the original inbound envelope, captured at acceptance
 *   so the worker that picks up the request later has everything it
 *   needs without a second lookup. Empty map when the request carries
 *   no body. Values must be JSON-serializable primitives, collections,
 *   or null — do not put domain objects or lambdas here.
 */
data class AsyncRequest(
    val id: String,
    val acceptedAtEpochMillis: Long,
    val payload: Map<String, Any?> = emptyMap(),
)
