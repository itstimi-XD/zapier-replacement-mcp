package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.MultiBranchRouter
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.RoutingRule
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Guards the load-bearing `catch (c: CancellationException) { throw c }`
 * inside [RouteEventUseCase]. The rethrow is what keeps structured
 * concurrency intact when a parent scope (e.g. `withTimeout`) cancels —
 * if a future "simplification" pass deletes that branch, this test is
 * what fails before the bug reaches production.
 *
 * Without the rethrow, the generic `Throwable` catch would swallow
 * `CancellationException` into a [DispatchResult.Failed], the
 * `withTimeout` parent would never observe the cancellation, and the
 * use case would silently continue past its deadline.
 */
class RouteEventUseCaseCancellationTest {

    @Test
    fun `cancellation propagates through the use case to the caller`() = runTest {
        val rules = listOf(
            RoutingRule("r1", "slow", NotificationChannel.SMS) { true },
        )
        val dispatcher = HangingDispatcher()

        val useCase = RouteEventUseCase(
            router = MultiBranchRouter(rules),
            dispatcher = dispatcher,
            messageBuilder = { branch, evt -> DispatchMessage(branch.branchName, evt.id) },
        )

        // The parent scope's timeout MUST be observable to the caller.
        // If RouteEventUseCase silently swallows CancellationException, the use
        // case returns a normal RouteEventResult (with a Failed branch), the
        // withTimeout block completes successfully, and assertFailsWith fails
        // with "expected exception not thrown" — the regression we want to catch.
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(timeMillis = 100L) {
                useCase.execute(WorkflowEvent("evt", "any", emptyMap()))
            }
        }
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
