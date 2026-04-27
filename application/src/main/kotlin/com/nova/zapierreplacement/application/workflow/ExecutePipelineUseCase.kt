package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.DispatchMessage
import com.nova.zapierreplacement.application.ports.DispatchResult
import com.nova.zapierreplacement.application.ports.MessageDispatchPort
import com.nova.zapierreplacement.domain.workflow.PipelineExecutionResult
import com.nova.zapierreplacement.domain.workflow.PipelineStep
import com.nova.zapierreplacement.domain.workflow.SequentialPipeline
import com.nova.zapierreplacement.domain.workflow.StepExecutionResult
import com.nova.zapierreplacement.domain.workflow.WorkflowEvent
import kotlinx.coroutines.CancellationException

/**
 * Replacement for the cascading-Zaps anti-pattern: execute every step of
 * a [SequentialPipeline] in order, stopping at the first failure.
 *
 * Semantics mirror the original Zap chain — if Zap B never fired because
 * Zap A failed, the same logic applies here: a failed step terminates
 * the pipeline and the remaining steps never run. The aggregate result
 * captures the partial trail so the caller can decide retry / alerting.
 *
 * Note on concurrency: pipelines are sequential by definition, so there
 * is no [supervisorScope] / parallel concern here. We still re-throw
 * [CancellationException] so callers can wrap with `withTimeout` and
 * have it actually take effect.
 */
class ExecutePipelineUseCase(
    private val pipeline: SequentialPipeline,
    private val dispatcher: MessageDispatchPort,
    private val messageBuilder: (PipelineStep, WorkflowEvent) -> DispatchMessage,
) {

    suspend fun execute(event: WorkflowEvent): PipelineExecutionResult {
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

        return PipelineExecutionResult(
            event = event,
            stepResults = collected.toList(),
            completed = failedStepId == null,
            failedAtStepId = failedStepId,
        )
    }
}
