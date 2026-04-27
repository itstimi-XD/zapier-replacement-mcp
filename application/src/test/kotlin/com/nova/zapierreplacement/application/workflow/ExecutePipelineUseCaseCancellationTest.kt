package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.SequentialPipeline
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Mirrors [RouteEventUseCaseCancellationTest] for the pipeline use case.
 *
 * Pipelines are sequential, so the cancellation footgun is even more
 * dangerous here: a swallowed CancellationException would not just delay
 * one branch — it would let the entire chain run past its deadline.
 */
class ExecutePipelineUseCaseCancellationTest {

    @Test
    fun `cancellation propagates through pipeline execution to the caller`() = runTest {
        val pipeline = SequentialPipeline(
            listOf(
                PipelineStep("slow", "slow", NotificationChannel.SMS),
            ),
        )
        val dispatcher = HangingDispatcher()

        val useCase = ExecutePipelineUseCase(
            pipeline = pipeline,
            dispatcher = dispatcher,
            messageBuilder = { step, evt -> DispatchMessage(step.id, evt.id) },
        )

        // If ExecutePipelineUseCase silently swallows CancellationException,
        // the use case returns a normal PipelineExecutionResult, withTimeout
        // sees no exception, and assertFailsWith fails — exactly the
        // regression we want to catch.
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
            delay(timeMillis = Long.MAX_VALUE)
            return DispatchResult.Sent("never")
        }
    }
}
