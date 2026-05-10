package com.nova.zapierreplacement.domain.workflow

/**
 * Pure matching logic for the polling-to-webhook anti-pattern.
 *
 * Given a list of [WebhookSubscription]s and an inbound [WebhookEvent],
 * picks the first subscription whose source / eventType / matcher accept
 * the event and extracts its dedup key. No I/O, no idempotency store —
 * persistence and dispatch are the application layer's job.
 *
 * First-match semantics are deliberate: webhook receivers are expected
 * to map an inbound event to exactly one logical action. If you need to
 * fan out, route the matched event into a [MultiBranchRouter] downstream.
 */
class WebhookEventReceiver(subscriptions: List<WebhookSubscription>) {

    init {
        val duplicates = subscriptions.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Subscription IDs must be unique. Duplicates: $duplicates"
        }
    }

    private val subscriptions: List<WebhookSubscription> = subscriptions.toList()

    fun receive(event: WebhookEvent): SubscriptionMatch {
        val matched = subscriptions.firstOrNull { sub ->
            sub.source == event.source &&
                sub.eventType == event.eventType &&
                sub.matcher(event)
        }

        return SubscriptionMatch(
            event = event,
            matched = matched,
            dedupKey = matched?.dedupKeyExtractor?.invoke(event),
        )
    }
}
