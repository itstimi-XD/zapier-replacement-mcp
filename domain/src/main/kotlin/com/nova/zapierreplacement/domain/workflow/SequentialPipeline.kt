package com.nova.zapierreplacement.domain.workflow

/**
 * Sequential execution plan for the cascading-Zaps anti-pattern.
 *
 * In Zapier, the typical "Zap A → webhook → Zap B → webhook → Zap C"
 * chain multiplies billable tasks per business event. Replacing the
 * chain with a single in-process sequence collapses that to one task
 * budget (zero, on a self-hosted backend) and removes the per-hop latency.
 *
 * Steps execute in declaration order. Failure semantics are decided by
 * the application layer — this domain type only commits to the order.
 *
 * Step IDs must be unique so callers can correlate per-step outcomes
 * back to the plan without ambiguity.
 */
class SequentialPipeline(steps: List<PipelineStep>) {

    init {
        val duplicates = steps.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Pipeline step IDs must be unique. Duplicates: $duplicates"
        }
    }

    /** Defensive copy so external mutation can't break order or uniqueness. */
    val steps: List<PipelineStep> = steps.toList()
}
