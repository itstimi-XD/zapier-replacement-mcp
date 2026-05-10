package com.nova.zapierreplacement.domain.workflow

/**
 * A named, pure transformation that derives additional fields from a
 * [WorkflowEvent]'s current payload.
 *
 * Replacement primitive for the "Code by Zapier" anti-pattern: instead
 * of inline Python/JS scripts that run inside a Zap step (one task per
 * event, 1-10 second timeout, untestable, unshareable), enrichments
 * live as plain Kotlin functions in your backend — testable in
 * isolation, reusable across workflows, no per-run cost, no timeout.
 *
 * - [id] / [name] are for observability and version tracking. When a
 *   payload reads "field X was enriched at v3", you can correlate back
 *   to the specific enrichment instance that produced it.
 * - [derive] receives the *current* payload (which may already include
 *   prior enrichments' output) and returns ONLY the new fields it
 *   wants to contribute. Returning an empty map is fine — the contract
 *   is "produce the deltas you own."
 *
 *   Key conflicts are resolved by the pipeline: if this enrichment
 *   returns a key already present (either from the original event
 *   payload or a prior enrichment), the pipeline's later-wins policy
 *   applies — the newer value replaces the old one. There is no
 *   mechanism to detect or prevent this at the enrichment level; if
 *   strict non-overlap is required, validate in tests.
 *
 * Enrichments are pure: keep all I/O behind ports. If your derivation
 * needs to call HubSpot, that's an application-layer step, not an
 * enrichment.
 */
data class EventEnrichment(
    val id: String,
    val name: String,
    val derive: (Map<String, Any?>) -> Map<String, Any?>,
)
