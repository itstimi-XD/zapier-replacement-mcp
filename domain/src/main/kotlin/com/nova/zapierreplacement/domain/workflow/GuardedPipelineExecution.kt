package com.nova.zapierreplacement.domain.workflow

/**
 * Outcome of running a [GuardedPipeline] against a single event.
 *
 * The [Skipped] / [Executed] split is intentional: callers should be
 * able to tell at a glance whether *any* downstream side effects fired.
 * Conflating "gate rejected the event" with "gate passed but all steps
 * happened to no-op" loses the property that makes guarded pipelines
 * worth modeling separately from [SequentialPipeline].
 */
sealed interface GuardedPipelineExecution {

    val event: WorkflowEvent

    /** Gate rejected the event; no [PipelineStep] was attempted. */
    data class Skipped(
        override val event: WorkflowEvent,
        val gate: GuardCondition,
    ) : GuardedPipelineExecution

    /** Gate accepted the event; [pipelineResult] reports the per-step outcome. */
    data class Executed(
        override val event: WorkflowEvent,
        val gate: GuardCondition,
        val pipelineResult: PipelineExecutionResult,
    ) : GuardedPipelineExecution
}
