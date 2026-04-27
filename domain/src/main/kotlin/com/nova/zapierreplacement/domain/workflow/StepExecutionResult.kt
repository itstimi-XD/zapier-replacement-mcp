package com.nova.zapierreplacement.domain.workflow

/**
 * Per-step outcome inside a [PipelineExecutionResult]. The `outcome` is
 * an opaque marker the domain doesn't interpret — the application layer
 * fills it with adapter-specific success / failure data and the caller
 * decides retry / DLQ / alerting policy from there.
 *
 * Outcome is `Any?` deliberately — the domain knows nothing about the
 * dispatcher's result types. Keep this layer thin.
 */
data class StepExecutionResult(
    val step: PipelineStep,
    val outcome: Any?,
    val succeeded: Boolean,
)
