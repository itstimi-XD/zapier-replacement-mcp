package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.IdempotencyStorePort
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.WebhookEvent
import com.nova.zapierreplacement.domain.workflow.WebhookEventReceiver
import com.nova.zapierreplacement.domain.workflow.WebhookSubscription
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Guards the load-bearing `catch (c: CancellationException) { throw c }`
 * inside [ReceiveWebhookEventUseCase]. Mirrors [RouteEventUseCaseCancellationTest]
 * for the dispatch path of the polling-to-webhook use case — if a future
 * "simplification" pass deletes that branch, this test is what fails before
 * the bug reaches production.
 *
 * Without the rethrow, the catch-Exception clause would swallow
 * `CancellationException` into [DispatchResult.Failed], the `withTimeout`
 * parent would never observe the cancellation, and the use case would
 * silently continue past its deadline.
 */
class ReceiveWebhookEventUseCaseCancellationTest {

    @Test
    fun `cancellation propagates through the use case to the caller`() = runTest {
        val sub = WebhookSubscription(
            id = "sub",
            source = "stripe",
            eventType = "customer.updated",
            targetChannel = NotificationChannel.SLACK,
            dedupKeyExtractor = { "${it.body["id"]}" },
        )
        val store = AlwaysFreshIdempotencyStore()
        val dispatcher = HangingDispatcher()

        val useCase = ReceiveWebhookEventUseCase(
            receiver = WebhookEventReceiver(listOf(sub)),
            idempotencyStore = store,
            dispatcher = dispatcher,
            messageBuilder = { matched, _ -> DispatchMessage(matched.id, "body") },
        )

        // The parent scope's timeout MUST be observable to the caller.
        // If the use case silently swallows CancellationException, execute()
        // returns a Dispatched(Failed(...)) result, withTimeout completes
        // successfully, and assertFailsWith fails — the regression we want.
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(timeMillis = 100L) {
                useCase.execute(
                    WebhookEvent(
                        source = "stripe",
                        eventType = "customer.updated",
                        body = mapOf("id" to "evt_cancel"),
                        receivedAtEpochMillis = 0L,
                    ),
                )
            }
        }
    }

    private class AlwaysFreshIdempotencyStore : IdempotencyStorePort {
        override suspend fun markSeenIfAbsent(key: String): Boolean = true
    }

    private class HangingDispatcher : MessageDispatchPort {
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            // Cooperatively cancellable suspend — far longer than any test timeout.
            delay(timeMillis = Long.MAX_VALUE)
            return DispatchResult.Sent("never")
        }
    }
}
