package com.nova.zapierreplacement.domain.workflow

/**
 * Result of routing an inbound [WebhookEvent] through a list of
 * [WebhookSubscription]s.
 *
 * [matched] is null when no subscription's source/eventType/matcher
 * accepted the event — the caller typically responds 200 OK to the
 * provider anyway (so the provider stops retrying) and drops it. When
 * non-null, [dedupKey] carries the value extracted by the matched
 * subscription's [WebhookSubscription.dedupKeyExtractor], ready for
 * the application layer to consult its idempotency store.
 *
 * First-match semantics: if more than one subscription qualifies for a
 * given event, the first one in declaration order wins. Multi-branch
 * fan-out is a different anti-pattern (see [MultiBranchRouter]) and
 * intentionally lives elsewhere.
 */
data class SubscriptionMatch(
    val event: WebhookEvent,
    val matched: WebhookSubscription?,
    val dedupKey: String?,
) {
    init {
        require((matched == null) == (dedupKey == null)) {
            "matched and dedupKey must be jointly null or jointly non-null"
        }
    }
}
