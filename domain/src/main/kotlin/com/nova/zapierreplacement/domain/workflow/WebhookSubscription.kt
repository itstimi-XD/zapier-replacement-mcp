package com.nova.zapierreplacement.domain.workflow

/**
 * Declarative subscription to a class of webhook deliveries.
 *
 * Intent: replace a polling Zap ("every 15 minutes, ask Stripe if anything
 * changed") with a push subscription against the same source — the
 * subscription describes which events to react to, and how to derive a
 * stable dedup key so retried/duplicated deliveries don't double-fire.
 *
 * - [source] / [eventType] are matched verbatim against the inbound
 *   [WebhookEvent]. Use the provider's own naming (e.g. "stripe" +
 *   "customer.updated") so subscriptions read like the provider's docs.
 * - [matcher] is the optional finer predicate when source/type are not
 *   selective enough — e.g. only customers whose plan starts with "pro".
 * - [dedupKeyExtractor] MUST return a value that is stable across
 *   re-deliveries of the same logical event (typically the provider's
 *   event ID). Returning a non-stable value breaks the dedup contract
 *   and the application layer will dispatch the same event multiple
 *   times.
 *
 * The subscription is config: keep all I/O out of it. Anything that
 * needs network or persistence belongs in the application layer.
 *
 * Note: this is a `data class` for the convenience of [copy] and KDoc
 * generation, but be aware that the auto-generated `equals` / `hashCode`
 * compare lambda properties by reference. Two structurally identical
 * subscriptions whose [matcher] / [dedupKeyExtractor] were constructed
 * separately will not be equal. Treat instances as opaque after
 * construction; do not rely on subscription equality for set membership
 * or map keying.
 */
data class WebhookSubscription(
    val id: String,
    val source: String,
    val eventType: String,
    val targetChannel: NotificationChannel,
    val dedupKeyExtractor: (WebhookEvent) -> String,
    val matcher: (WebhookEvent) -> Boolean = { true },
)
