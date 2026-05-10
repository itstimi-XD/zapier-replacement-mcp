package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.AsyncRequestRegistryPort
import com.nova.zapierreplacement.domain.workflow.AsyncRequest
import com.nova.zapierreplacement.domain.workflow.AsyncRequestState

/**
 * Receives a webhook delivery, persists it as [AsyncRequestState.Accepted],
 * and returns the request id immediately so the API layer can reply
 * with HTTP 202 in milliseconds.
 *
 * Replacement for the synchronous-webhook-chain anti-pattern: in
 * Zapier, an inbound webhook → external API → wait → respond chain
 * stacks latency across every hop and bills a task each. Here, the
 * sender is unblocked the moment the registry confirms persistence;
 * the actual work runs on the worker's clock and either calls back
 * via a registered callback URL or is observable through a status
 * polling endpoint.
 *
 * Why each piece exists:
 * - [registry] is the only side effect — accept-side use case is
 *   intentionally thin; the worker that picks up the request is a
 *   separate concern.
 * - [idGenerator] is injected so the use case stays deterministic in
 *   tests. Production wiring uses a UUID generator.
 * - [clock] is injected so [AsyncRequest.acceptedAtEpochMillis] is
 *   testable without freezing system time.
 *
 * The use case does not validate the request payload. Validation is
 * either an enrichment step that runs before this (return early at
 * the API layer, never persist) or a worker-side check that
 * transitions Accepted → Failed on pickup. Embedding validation here
 * would couple acceptance to schema knowledge that does not belong
 * in this layer.
 */
class AcceptAsyncRequestUseCase(
    private val registry: AsyncRequestRegistryPort,
    private val idGenerator: () -> String,
    private val clock: () -> Long,
) {

    /**
     * Accept an inbound delivery and persist its initial state.
     *
     * @param payload the raw inbound envelope (request body). Captured
     *        on [AsyncRequest] so the worker that picks up `Accepted`
     *        later has everything it needs without a second lookup.
     *        Pass an empty map when the request carries no body.
     */
    suspend fun execute(payload: Map<String, Any?> = emptyMap()): AsyncRequest {
        val request = AsyncRequest(
            id = idGenerator(),
            acceptedAtEpochMillis = clock(),
            payload = payload.toMap(),
        )
        registry.persistAccepted(AsyncRequestState.Accepted(request))
        return request
    }
}
