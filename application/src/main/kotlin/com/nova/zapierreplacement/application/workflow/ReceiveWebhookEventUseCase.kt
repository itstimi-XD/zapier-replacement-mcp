package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.IdempotencyStorePort
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.WebhookEvent
import com.nova.zapierreplacement.domain.workflow.WebhookEventReceiver
import com.nova.zapierreplacement.domain.workflow.WebhookSubscription
import kotlinx.coroutines.CancellationException

/**
 * Replacement for the polling-instead-of-webhook anti-pattern: take an
 * inbound webhook delivery, decide if any subscription cares about it,
 * deduplicate against the idempotency store, and dispatch the matched
 * action exactly once.
 *
 * Why each step exists:
 * - [receiver] runs the pure source/eventType/matcher routing in domain.
 * - [idempotencyStore] survives provider retries and at-least-once delivery
 *   networks. Without it, a 5xx + retry double-fires the workflow.
 * - [dispatcher] is the same outbound port the other use cases share —
 *   one adapter set, one mental model.
 *
 * Failure semantics: an adapter exception while dispatching is captured
 * as [DispatchResult.Failed] and surfaced to the caller; the dedup key
 * is NOT rolled back. Re-delivery from the provider for the same event
 * will short-circuit as [ReceiveWebhookResult.Duplicate]. Choose this
 * policy because most webhook providers expect the receiver to ack
 * quickly (200) and run the actual work async — silent retries to the
 * same external system cause more pain than the occasional dropped
 * dispatch, which can be replayed manually from the idempotency store.
 */
class ReceiveWebhookEventUseCase(
    private val receiver: WebhookEventReceiver,
    private val idempotencyStore: IdempotencyStorePort,
    private val dispatcher: MessageDispatchPort,
    private val messageBuilder: (WebhookSubscription, WebhookEvent) -> DispatchMessage,
) {

    suspend fun execute(event: WebhookEvent): ReceiveWebhookResult {
        val match = receiver.receive(event)
        val subscription = match.matched ?: return ReceiveWebhookResult.NoMatch
        val dedupKey = match.dedupKey
            ?: error("SubscriptionMatch invariant: matched without dedupKey")

        val firstTime = idempotencyStore.markSeenIfAbsent(dedupKey)
        if (!firstTime) {
            return ReceiveWebhookResult.Duplicate(subscription, dedupKey)
        }

        val message = messageBuilder(subscription, event)
        val outcome = try {
            dispatcher.dispatch(subscription.targetChannel, message)
        } catch (c: CancellationException) {
            // Re-throw so callers using `withTimeout`/structured cancellation work.
            throw c
        } catch (e: Exception) {
            // Catch Exception, not Throwable — Errors (OOM, StackOverflow,
            // LinkageError) must propagate so the JVM can surface them.
            DispatchResult.Failed(reason = e.message ?: "unknown")
        }

        return ReceiveWebhookResult.Dispatched(
            subscription = subscription,
            dedupKey = dedupKey,
            outcome = outcome,
        )
    }
}

sealed interface ReceiveWebhookResult {

    /** No subscription accepted the event — caller still ACKs the provider so it stops retrying. */
    data object NoMatch : ReceiveWebhookResult

    /** Already-seen dedup key — workflow already dispatched on a prior delivery. */
    data class Duplicate(
        val subscription: WebhookSubscription,
        val dedupKey: String,
    ) : ReceiveWebhookResult

    /** First time this dedup key is seen — dispatcher attempted, [outcome] reports success or failure. */
    data class Dispatched(
        val subscription: WebhookSubscription,
        val dedupKey: String,
        val outcome: DispatchResult,
    ) : ReceiveWebhookResult
}
