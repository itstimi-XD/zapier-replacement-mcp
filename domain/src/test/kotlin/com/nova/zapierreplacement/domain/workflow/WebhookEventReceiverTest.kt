package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebhookEventReceiverTest {

    @Test
    fun `matches first subscription whose source eventType and matcher all pass`() {
        val subs = listOf(
            sub(id = "sub-1", source = "stripe", eventType = "customer.created"),
            sub(id = "sub-2", source = "stripe", eventType = "customer.updated"),
            sub(id = "sub-3", source = "github", eventType = "push"),
        )
        val receiver = WebhookEventReceiver(subs)

        val event = event(source = "stripe", eventType = "customer.updated", body = mapOf("id" to "evt_42"))

        val result = receiver.receive(event)

        assertEquals("sub-2", result.matched?.id)
        assertEquals("evt_42", result.dedupKey)
    }

    @Test
    fun `applies first-match semantics in declaration order`() {
        // Two subscriptions both qualify; the earlier one wins.
        val subs = listOf(
            sub(id = "winner", source = "stripe", eventType = "customer.updated"),
            sub(id = "also-matches", source = "stripe", eventType = "customer.updated"),
        )
        val receiver = WebhookEventReceiver(subs)

        val result = receiver.receive(event(source = "stripe", eventType = "customer.updated"))

        assertEquals("winner", result.matched?.id)
    }

    @Test
    fun `returns no match when source differs`() {
        val receiver = WebhookEventReceiver(
            listOf(sub(id = "s", source = "stripe", eventType = "customer.updated")),
        )

        val result = receiver.receive(event(source = "github", eventType = "customer.updated"))

        assertNull(result.matched)
        assertNull(result.dedupKey)
    }

    @Test
    fun `returns no match when eventType differs`() {
        val receiver = WebhookEventReceiver(
            listOf(sub(id = "s", source = "stripe", eventType = "customer.updated")),
        )

        val result = receiver.receive(event(source = "stripe", eventType = "customer.deleted"))

        assertNull(result.matched)
    }

    @Test
    fun `respects subscription matcher predicate when source and eventType match`() {
        val onlyProPlan = sub(
            id = "pro-only",
            source = "stripe",
            eventType = "customer.updated",
            matcher = { (it.body["plan"] as? String)?.startsWith("pro") == true },
        )
        val receiver = WebhookEventReceiver(listOf(onlyProPlan))

        val proEvent = event(source = "stripe", eventType = "customer.updated", body = mapOf("plan" to "pro-monthly", "id" to "evt_1"))
        val starterEvent = event(source = "stripe", eventType = "customer.updated", body = mapOf("plan" to "starter", "id" to "evt_2"))

        assertEquals("pro-only", receiver.receive(proEvent).matched?.id)
        assertNull(receiver.receive(starterEvent).matched)
    }

    @Test
    fun `match preserves the original event for downstream consumers`() {
        val incoming = event(source = "stripe", eventType = "customer.updated", body = mapOf("id" to "evt_99"))
        val receiver = WebhookEventReceiver(
            listOf(sub(id = "s", source = "stripe", eventType = "customer.updated")),
        )

        val result = receiver.receive(incoming)

        // Mirrors RoutingDecision.event — callers who use the receiver standalone
        // (without the use case) need the originating event back from the result.
        assertEquals(incoming, result.event)
    }

    @Test
    fun `dedupKey is extracted by the matched subscription`() {
        val receiver = WebhookEventReceiver(
            listOf(
                sub(
                    id = "s",
                    source = "github",
                    eventType = "push",
                    dedupKey = { "${it.body["delivery"]}" },
                ),
            ),
        )

        val event = event(source = "github", eventType = "push", body = mapOf("delivery" to "abc-123"))

        assertEquals("abc-123", receiver.receive(event).dedupKey)
    }

    @Test
    fun `rejects construction with duplicate subscription ids`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            WebhookEventReceiver(
                listOf(
                    sub(id = "dup", source = "a", eventType = "x"),
                    sub(id = "dup", source = "b", eventType = "y"),
                ),
            )
        }
        assertEquals(true, ex.message?.contains("dup"))
    }

    @Test
    fun `mutating the input list after construction does not affect routing`() {
        val mutable = mutableListOf(
            sub(id = "original", source = "stripe", eventType = "customer.updated"),
        )
        val receiver = WebhookEventReceiver(mutable)

        // Reach in and try to break it.
        mutable.clear()
        mutable.add(sub(id = "injected", source = "stripe", eventType = "customer.updated"))

        val result = receiver.receive(event(source = "stripe", eventType = "customer.updated"))

        assertEquals("original", result.matched?.id)
    }

    private fun sub(
        id: String,
        source: String,
        eventType: String,
        targetChannel: NotificationChannel = NotificationChannel.SLACK,
        dedupKey: (WebhookEvent) -> String = { "${it.body["id"]}" },
        matcher: (WebhookEvent) -> Boolean = { true },
    ) = WebhookSubscription(
        id = id,
        source = source,
        eventType = eventType,
        targetChannel = targetChannel,
        dedupKeyExtractor = dedupKey,
        matcher = matcher,
    )

    private fun event(
        source: String,
        eventType: String,
        body: Map<String, Any?> = emptyMap(),
        receivedAtEpochMillis: Long = 1_700_000_000_000,
    ) = WebhookEvent(
        source = source,
        eventType = eventType,
        body = body,
        receivedAtEpochMillis = receivedAtEpochMillis,
    )
}
