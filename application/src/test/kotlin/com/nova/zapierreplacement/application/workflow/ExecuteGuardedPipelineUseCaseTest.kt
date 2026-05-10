package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.GuardCondition
import com.nova.zapierreplacement.domain.workflow.GuardedPipeline
import com.nova.zapierreplacement.domain.workflow.GuardedPipelineExecution
import com.nova.zapierreplacement.domain.workflow.NotificationChannel
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteGuardedPipelineUseCaseTest {

    private val event = WorkflowEvent(id = "evt", type = "any", payload = mapOf("country" to "US"))

    @Test
    fun `does not call dispatcher when gate rejects the event`() = runTest {
        val rejectingGate = GuardCondition("g", "always rejects") { false }
        val dispatcher = RecordingDispatcher()

        val result = useCase(
            GuardedPipeline(gate = rejectingGate, steps = listOf(step("a"), step("b"))),
            dispatcher,
        ).execute(event)

        val skipped = assertIs<GuardedPipelineExecution.Skipped>(result)
        assertEquals("g", skipped.gate.id)
        // The whole point of the pattern: zero expensive calls when the
        // predicate would have filtered the event out anyway.
        assertEquals(0, dispatcher.calls.size)
    }

    @Test
    fun `runs every step when gate accepts and all steps succeed`() = runTest {
        val acceptingGate = GuardCondition("g", "always accepts") { true }
        val dispatcher = RecordingDispatcher()

        val result = useCase(
            GuardedPipeline(gate = acceptingGate, steps = listOf(step("a"), step("b"), step("c"))),
            dispatcher,
        ).execute(event)

        val executed = assertIs<GuardedPipelineExecution.Executed>(result)
        assertEquals(true, executed.pipelineResult.completed)
        assertEquals(listOf("a", "b", "c"), executed.pipelineResult.stepResults.map { it.step.id })
        assertEquals(3, dispatcher.calls.size)
    }

    @Test
    fun `stops at first failed step and reports the partial trail`() = runTest {
        val acceptingGate = GuardCondition("g", "always accepts") { true }
        val dispatcher = SelectiveFailingDispatcher(failingRecipient = "b")

        val result = useCase(
            GuardedPipeline(gate = acceptingGate, steps = listOf(step("a"), step("b"), step("c"))),
            dispatcher,
        ).execute(event)

        val executed = assertIs<GuardedPipelineExecution.Executed>(result)
        assertEquals(false, executed.pipelineResult.completed)
        assertEquals("b", executed.pipelineResult.failedAtStepId)
        // Only "a" and "b" attempted; "c" must not have been touched.
        assertEquals(listOf("a", "b"), executed.pipelineResult.stepResults.map { it.step.id })
        assertEquals(2, dispatcher.calls.size)
    }

    @Test
    fun `gate uses the actual event payload`() = runTest {
        val onlyUS = GuardCondition("country-gate", "country=US") {
            it.payload["country"] == "US"
        }
        val dispatcher = RecordingDispatcher()
        val pipeline = GuardedPipeline(gate = onlyUS, steps = listOf(step("a")))
        val uc = useCase(pipeline, dispatcher)

        val usEvent = WorkflowEvent("e1", "any", mapOf("country" to "US"))
        val krEvent = WorkflowEvent("e2", "any", mapOf("country" to "KR"))

        assertIs<GuardedPipelineExecution.Executed>(uc.execute(usEvent))
        assertIs<GuardedPipelineExecution.Skipped>(uc.execute(krEvent))
        assertEquals(1, dispatcher.calls.size)
    }

    @Test
    fun `Executed result carries gate identity for observability`() = runTest {
        val gate = GuardCondition("named-gate", "country=US") { true }
        val dispatcher = RecordingDispatcher()

        val result = useCase(
            GuardedPipeline(gate = gate, steps = listOf(step("a"))),
            dispatcher,
        ).execute(event)

        val executed = assertIs<GuardedPipelineExecution.Executed>(result)
        assertEquals("named-gate", executed.gate.id)
    }

    @Test
    fun `empty pipeline with passing gate returns Executed completed with no steps`() = runTest {
        val acceptingGate = GuardCondition("g", "always accepts") { true }
        val dispatcher = RecordingDispatcher()

        val result = useCase(
            GuardedPipeline(gate = acceptingGate, steps = emptyList()),
            dispatcher,
        ).execute(event)

        val executed = assertIs<GuardedPipelineExecution.Executed>(result)
        assertEquals(true, executed.pipelineResult.completed)
        assertTrue(executed.pipelineResult.stepResults.isEmpty())
        assertEquals(0, dispatcher.calls.size)
    }

    @Test
    fun `adapter exception inside a step becomes Failed and stops the pipeline`() = runTest {
        val acceptingGate = GuardCondition("g", "always accepts") { true }
        val dispatcher = ThrowingDispatcher()

        val result = useCase(
            GuardedPipeline(gate = acceptingGate, steps = listOf(step("a"), step("b"))),
            dispatcher,
        ).execute(event)

        val executed = assertIs<GuardedPipelineExecution.Executed>(result)
        assertEquals(false, executed.pipelineResult.completed)
        assertEquals("a", executed.pipelineResult.failedAtStepId)
        val firstOutcome = executed.pipelineResult.stepResults.first().outcome
        val failed = assertIs<DispatchResult.Failed>(firstOutcome)
        assertEquals("simulated adapter failure", failed.reason)
    }

    private fun useCase(pipeline: GuardedPipeline, dispatcher: MessageDispatchPort) =
        ExecuteGuardedPipelineUseCase(
            pipeline = pipeline,
            dispatcher = dispatcher,
            messageBuilder = { step, evt -> DispatchMessage(step.id, evt.id) },
        )

    private fun step(id: String) = PipelineStep(
        id = id,
        name = "step-$id",
        targetChannel = NotificationChannel.SLACK,
    )

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

    private class SelectiveFailingDispatcher(
        private val failingRecipient: String,
    ) : MessageDispatchPort {
        val calls = mutableListOf<DispatchCall>()
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            calls += DispatchCall(channel, message)
            return if (message.recipient == failingRecipient) {
                DispatchResult.Failed(reason = "selective failure on $failingRecipient")
            } else {
                DispatchResult.Sent(externalId = "ok-${message.recipient}")
            }
        }
    }

    private class ThrowingDispatcher : MessageDispatchPort {
        override suspend fun dispatch(
            channel: NotificationChannel,
            message: DispatchMessage,
        ): DispatchResult {
            throw RuntimeException("simulated adapter failure")
        }
    }
}
