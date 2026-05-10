package com.nova.zapierreplacement.domain.workflow

/**
 * Pipeline whose steps run only after a [GuardCondition] passes.
 *
 * Replacement for the filter-after-expensive-steps Zapier anti-pattern:
 * by making the gate a separate, mandatory field — not just "the first
 * step that happens to be a filter" — the type forces the predicate to
 * run before any [PipelineStep]. There is no way to construct a
 * GuardedPipeline that evaluates an expensive step before the filter,
 * which is exactly the safety property the original anti-pattern lacks.
 *
 * Steps execute in declaration order once the gate passes; semantics
 * after that point are identical to [SequentialPipeline] — failures
 * stop the run and the application layer decides retry / DLQ.
 *
 * Step IDs must be unique so callers can correlate per-step outcomes
 * back to the plan; same invariant as [SequentialPipeline].
 */
class GuardedPipeline(
    val gate: GuardCondition,
    steps: List<PipelineStep>,
) {

    init {
        val duplicates = steps.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Pipeline step IDs must be unique. Duplicates: $duplicates"
        }
    }

    /** Defensive copy so external mutation can't break order or uniqueness. */
    val steps: List<PipelineStep> = steps.toList()
}
