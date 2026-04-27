package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.SequentialPipeline
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutePipelineUseCaseTest {

    private val event = WorkflowEvent(id = "evt-1", type = "any", payload = emptyMap())

    @Test
    fun `executes every step in declaration order on success`() = runTest {
        val pipeline = SequentialPipeline(
            listOf(
                PipelineStep("s1", "first", NotificationChannel.EMAIL),
                PipelineStep("s2", "second", NotificationChannel.SMS),
                PipelineStep("s3", "third", NotificationChannel.SLACK),
            ),
        )
        val dispatcher = OrderedRecordingDispatcher()

        val result = ExecutePipelineUseCase(
            pipeline = pipeline,
            dispatcher = dispatcher,
            messageBuilder = { step, evt -> DispatchMessage(step.id, evt.id) },
        ).execute(event)

        assertTrue(result.completed)
        assertEquals(null, result.failedAtStepId)
        assertEquals(listOf("s1", "s2", "s3"), result.stepResults.map { it.step.id })
        assertEquals(listOf("s1", "s2", "s3"), dispatcher.calls.map { it.recipient })
    }

    @Test
    fun `stops at first failed step and skips the rest`() = runTest {
        val pipeline = SequentialPipeline(
            listOf(
                PipelineStep("ok", "ok", NotificationChannel.EMAIL),
                PipelineStep("fail", "fail", NotificationChannel.SMS),
                PipelineStep("never", "never", NotificationChannel.SLACK),
            ),
        )
        val dispatcher = SelectiveFailingDispatcher(failingRecipient = "fail")

        val result = ExecutePipelineUseCase(
            pipeline = pipeline,
            dispatcher = dispatcher,
            messageBuilder = { step, evt -> DispatchMessage(step.id, evt.id) },
        ).execute(event)

        assertFalse(result.completed)
        assertEquals("fail", result.failedAtStepId)
        assertEquals(listOf("ok", "fail"), result.stepResults.map { it.step.id })
        assertTrue(result.stepResults[0].succeeded)
        assertFalse(result.stepResults[1].succeeded)
        // The thrown adapter exception's message should round-trip into the
        // recorded outcome — guards against a regression that changes the
        // mapping (e.g. wrapping or replacing the message).
        val failedOutcome = result.stepResults[1].outcome as DispatchResult.Failed
        assertEquals("simulated adapter failure", failedOutcome.reason)
        // The third step must never have been dispatched.
        assertEquals(listOf("ok", "fail"), dispatcher.callsAttempted)
    }

    @Test
    fun `empty pipeline yields completed=true with zero step results and no dispatch calls`() = runTest {
        val pipeline = SequentialPipeline(emptyList())
        val dispatcher = OrderedRecordingDispatcher()

        val result = ExecutePipelineUseCase(
            pipeline = pipeline,
            dispatcher = dispatcher,
            messageBuilder = { step, evt -> DispatchMessage(step.id, evt.id) },
        ).execute(event)

        assertTrue(result.completed)
        assertEquals(null, result.failedAtStepId)
        assertTrue(result.stepResults.isEmpty())
        assertTrue(dispatcher.calls.isEmpty())
    }

    private class OrderedRecordingDispatcher : MessageDispatchPort {
        val calls = mutableListOf<DispatchMessage>()
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            calls += message
            return DispatchResult.Sent(externalId = "ok-${calls.size}")
        }
    }

    private class SelectiveFailingDispatcher(
        private val failingRecipient: String,
    ) : MessageDispatchPort {
        val callsAttempted = mutableListOf<String>()
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            callsAttempted += message.recipient
            if (message.recipient == failingRecipient) {
                throw RuntimeException("simulated adapter failure")
            }
            return DispatchResult.Sent(externalId = "ok-${message.recipient}")
        }
    }
}
