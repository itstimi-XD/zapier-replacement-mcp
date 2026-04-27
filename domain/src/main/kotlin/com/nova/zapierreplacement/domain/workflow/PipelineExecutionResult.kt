package com.nova.zapierreplacement.domain.workflow

/**
 * Aggregate outcome of running a [SequentialPipeline] against a single event.
 *
 * Contains every step that *was attempted* — if the run stopped early
 * because of a failure, [stepResults] is the partial trail and
 * [completed] is false. Steps after the failed one are not included.
 */
data class PipelineExecutionResult(
    val event: WorkflowEvent,
    val stepResults: List<StepExecutionResult>,
    val completed: Boolean,
    val failedAtStepId: String?,
)
