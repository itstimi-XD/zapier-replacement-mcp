package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.GuardCondition
import com.nova.zapierreplacement.domain.workflow.GuardedPipeline
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Guards the load-bearing `catch (c: CancellationException) { throw c }`
 * inside [ExecuteGuardedPipelineUseCase]. Mirrors the cancellation tests
 * on the other use cases — if a future "simplification" pass deletes
 * that branch, this test fails before the bug reaches production.
 *
 * Without the rethrow, the catch-Exception clause would swallow
 * `CancellationException` into [DispatchResult.Failed], the `withTimeout`
 * parent would never observe the cancellation, and the use case would
 * silently continue past its deadline.
 */
class ExecuteGuardedPipelineUseCaseCancellationTest {

    @Test
    fun `cancellation propagates through the use case to the caller`() = runTest {
        val gate = GuardCondition("g", "always accepts") { true }
        val pipeline = GuardedPipeline(
            gate = gate,
            steps = listOf(
                PipelineStep(id = "slow", name = "slow", targetChannel = NotificationChannel.SLACK),
            ),
        )
        val useCase = ExecuteGuardedPipelineUseCase(
            pipeline = pipeline,
            dispatcher = HangingDispatcher(),
            messageBuilder = { step, evt -> DispatchMessage(step.name, evt.id) },
        )

        // The parent scope's timeout MUST be observable to the caller.
        // If the use case silently swallows CancellationException, execute()
        // returns a normal Executed(...) result, withTimeout completes
        // successfully, and assertFailsWith fails — the regression we want.
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
