package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.GuardedPipeline
import com.nova.zapierreplacement.domain.workflow.GuardedPipelineExecution
import com.nova.zapierreplacement.domain.workflow.PipelineExecutionResult
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.StepExecutionResult
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.CancellationException

/**
 * Replacement for the filter-after-expensive-steps Zapier anti-pattern:
 * evaluate the gate first, only run the expensive steps if it passes,
 * stop the run on the first step failure.
 *
 * Why each piece exists:
 * - The gate predicate is the whole point of the pattern — it runs
 *   once, before any [dispatcher] call, so we never burn an external
 *   API quota for an event that was going to be filtered out anyway.
 * - The post-gate step loop mirrors [ExecutePipelineUseCase] semantics
 *   intentionally; once we're past the gate, the cascading-Zaps fix
 *   applies as-is — fail fast on the first failed step, surface the
 *   partial trail to the caller.
 *
 * Cancellation: re-throw [CancellationException] so callers can wrap
 * the use case with `withTimeout` and have it actually take effect.
 *
 * Returns [GuardedPipelineExecution.Skipped] when the gate rejects the
 * event (no [PipelineStep] attempted, no dispatcher call), or
 * [GuardedPipelineExecution.Executed] when the gate passes — its
 * embedded [PipelineExecutionResult] reports the per-step trail and
 * whether the run completed or stopped at a failure.
 */
class ExecuteGuardedPipelineUseCase(
    private val pipeline: GuardedPipeline,
    private val dispatcher: MessageDispatchPort,
    private val messageBuilder: (PipelineStep, WorkflowEvent) -> DispatchMessage,
) {

    suspend fun execute(event: WorkflowEvent): GuardedPipelineExecution {
        if (!pipeline.gate.predicate(event)) {
            return GuardedPipelineExecution.Skipped(event = event, gate = pipeline.gate)
        }

        val collected = mutableListOf<StepExecutionResult>()
        var failedStepId: String? = null

        for (step in pipeline.steps) {
            val message = messageBuilder(step, event)

            val dispatchResult = try {
                dispatcher.dispatch(step.targetChannel, message)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // Catch Exception, not Throwable — Errors (OOM, StackOverflow,
                // LinkageError) must propagate so the JVM can surface them.
                DispatchResult.Failed(reason = e.message ?: "unknown")
            }

            val succeeded = dispatchResult is DispatchResult.Sent
            collected += StepExecutionResult(
                step = step,
                outcome = dispatchResult,
                succeeded = succeeded,
            )

            if (!succeeded) {
                failedStepId = step.id
                break
            }
        }

        val pipelineResult = PipelineExecutionResult(
            event = event,
            stepResults = collected.toList(),
            completed = failedStepId == null,
            failedAtStepId = failedStepId,
        )

        return GuardedPipelineExecution.Executed(
            event = event,
            gate = pipeline.gate,
            pipelineResult = pipelineResult,
        )
    }
}
