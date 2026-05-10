package com.nova.zapierreplacement.domain.workflow

/**
 * Aggregate outcome of running a pipeline against a single event.
 *
 * Used by both [SequentialPipeline] and the post-gate phase of
 * [GuardedPipeline] — once a guarded pipeline's gate passes, its
 * step semantics are identical to the sequential case, so the same
 * result type applies.
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
