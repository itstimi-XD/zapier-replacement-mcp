package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.IdempotencyStorePort
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.WebhookEvent
import com.nova.zapierreplacement.domain.workflow.WebhookEventReceiver
import com.nova.zapierreplacement.domain.workflow.WebhookSubscription
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReceiveWebhookEventUseCaseTest {

    @Test
    fun `dispatches once when event is first seen`() = runTest {
        val sub = subscription(id = "stripe-customer-updated")
        val store = RecordingIdempotencyStore()
        val dispatcher = RecordingDispatcher()

        val result = useCase(listOf(sub), store, dispatcher).execute(
            event(source = "stripe", eventType = "customer.updated", id = "evt_1"),
        )

        val dispatched = assertIs<ReceiveWebhookResult.Dispatched>(result)
        assertEquals("stripe-customer-updated", dispatched.subscription.id)
        assertEquals("evt_1", dispatched.dedupKey)
        assertIs<DispatchResult.Sent>(dispatched.outcome)
        assertEquals(1, dispatcher.calls.size)
        assertEquals(NotificationChannel.SLACK, dispatcher.calls.single().channel)
    }

    @Test
    fun `short-circuits as Duplicate on second delivery of the same dedup key`() = runTest {
        val sub = subscription(id = "stripe-customer-updated")
        val store = RecordingIdempotencyStore()
        val dispatcher = RecordingDispatcher()
        val uc = useCase(listOf(sub), store, dispatcher)

        val first = uc.execute(event(source = "stripe", eventType = "customer.updated", id = "evt_1"))
        val second = uc.execute(event(source = "stripe", eventType = "customer.updated", id = "evt_1"))

        assertIs<ReceiveWebhookResult.Dispatched>(first)
        val dup = assertIs<ReceiveWebhookResult.Duplicate>(second)
        assertEquals("evt_1", dup.dedupKey)
        // Critically: the dispatcher must not have been called twice.
        assertEquals(1, dispatcher.calls.size)
    }

    @Test
    fun `returns NoMatch and never touches store or dispatcher when nothing matches`() = runTest {
        val sub = subscription(id = "stripe-only", source = "stripe")
        val store = RecordingIdempotencyStore()
        val dispatcher = RecordingDispatcher()

        val result = useCase(listOf(sub), store, dispatcher).execute(
            event(source = "github", eventType = "push", id = "delivery_1"),
        )

        assertIs<ReceiveWebhookResult.NoMatch>(result)
        assertTrue(store.recorded.isEmpty())
        assertTrue(dispatcher.calls.isEmpty())
    }

    @Test
    fun `adapter exception during dispatch becomes Failed outcome and dedup is preserved`() = runTest {
        val sub = subscription(id = "stripe-customer-updated")
        val store = RecordingIdempotencyStore()
        val dispatcher = AlwaysFailingDispatcher()

        val result = useCase(listOf(sub), store, dispatcher).execute(
            event(source = "stripe", eventType = "customer.updated", id = "evt_2"),
        )

        val dispatched = assertIs<ReceiveWebhookResult.Dispatched>(result)
        val failed = assertIs<DispatchResult.Failed>(dispatched.outcome)
        assertEquals("simulated adapter failure", failed.reason)
        // Documenting the policy: dedup key is NOT rolled back on dispatch failure.
        // A retried delivery from the provider will short-circuit as Duplicate.
        assertEquals(setOf("evt_2"), store.recorded)
    }

    @Test
    fun `messageBuilder receives the matched subscription and the original event`() = runTest {
        val sub = subscription(
            id = "github-push",
            source = "github",
            eventType = "push",
        )
        val store = RecordingIdempotencyStore()
        val dispatcher = RecordingDispatcher()

        val capturedEvents = mutableListOf<Pair<String, String>>()
        val uc = ReceiveWebhookEventUseCase(
            receiver = WebhookEventReceiver(listOf(sub)),
            idempotencyStore = store,
            dispatcher = dispatcher,
            messageBuilder = { matched, evt ->
                capturedEvents += matched.id to (evt.body["id"] as String)
                DispatchMessage(recipient = matched.id, body = evt.eventType)
            },
        )

        uc.execute(event(source = "github", eventType = "push", id = "delivery_99"))

        assertEquals(listOf("github-push" to "delivery_99"), capturedEvents)
        assertEquals("github-push", dispatcher.calls.single().message.recipient)
    }

    private fun useCase(
        subs: List<WebhookSubscription>,
        store: IdempotencyStorePort,
        dispatcher: MessageDispatchPort,
    ) = ReceiveWebhookEventUseCase(
        receiver = WebhookEventReceiver(subs),
        idempotencyStore = store,
        dispatcher = dispatcher,
        messageBuilder = { sub, _ -> DispatchMessage(recipient = sub.id, body = "test") },
    )

    private fun subscription(
        id: String,
        source: String = "stripe",
        eventType: String = "customer.updated",
    ) = WebhookSubscription(
        id = id,
        source = source,
        eventType = eventType,
        targetChannel = NotificationChannel.SLACK,
        dedupKeyExtractor = { "${it.body["id"]}" },
    )

    private fun event(
        source: String,
        eventType: String,
        id: String,
        receivedAtEpochMillis: Long = 1_700_000_000_000,
    ) = WebhookEvent(
        source = source,
        eventType = eventType,
        body = mapOf("id" to id),
        receivedAtEpochMillis = receivedAtEpochMillis,
    )

    private class RecordingIdempotencyStore : IdempotencyStorePort {
        val recorded = mutableSetOf<String>()
        override suspend fun markSeenIfAbsent(key: String): Boolean = recorded.add(key)
    }

    private data class DispatchCall(val channel: NotificationChannel, val message: DispatchMessage)

    private class RecordingDispatcher : MessageDispatchPort {
        val calls = mutableListOf<DispatchCall>()
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            calls += DispatchCall(channel, message)
            return DispatchResult.Sent(externalId = "ext-${calls.size}")
        }
    }

    private class AlwaysFailingDispatcher : MessageDispatchPort {
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            throw RuntimeException("simulated adapter failure")
        }
    }
}
