package com.nova.zapierreplacement.domain.workflow

/**
 * A predicate that decides whether the rest of a pipeline runs at all.
 *
 * The polling-to-webhook anti-pattern's twin failure mode: in Zapier,
 * a Filter step often lands at the *end* of a chain, after several
 * expensive steps (HubSpot lookup, OpenAI call, etc.) have already
 * burned tasks and external API quota for events that ultimately get
 * filtered out. The fix is to evaluate the predicate first.
 *
 * Modeling it as its own type — rather than "the first [PipelineStep]
 * with a special flag" — makes the gate-first contract visible at the
 * type level: a [GuardedPipeline] *cannot* be constructed without one.
 *
 * [id] / [description] exist for observability — when a result reports
 * `Skipped(gate)`, the caller can identify *which* gate rejected the
 * event without re-running predicates.
 */
data class GuardCondition(
    val id: String,
    val description: String,
    val predicate: (WorkflowEvent) -> Boolean,
)
