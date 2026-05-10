package com.nova.zapierreplacement.domain.workflow

/**
 * Result of running an [EnrichmentPipeline] against a single event.
 *
 * [event] is the new event value with the cumulative enriched payload —
 * what downstream stages should consume. [originalPayload] is preserved
 * so upstream consumers can observe what changed without re-running the
 * chain.
 *
 * [applied] reports each enrichment that ran and which keys it
 * contributed; this is how you answer "where did field X come from?"
 * questions weeks later in production.
 */
data class EnrichedEvent(
    val event: WorkflowEvent,
    val originalPayload: Map<String, Any?>,
    val applied: List<AppliedEnrichment>,
)

/**
 * Per-enrichment audit record.
 *
 * Stores only the enrichment's identity ([enrichmentId] / [enrichmentName])
 * rather than the full [EventEnrichment] — embedding the function value
 * would pin lambda captures for the lifetime of the result and break
 * downstream serialization (audit logs, MCP responses, etc.).
 *
 * [derivedKeys] is a [Set] because callers should not infer ordering
 * from the audit trail; it answers "which keys did this enrichment
 * touch?" not "in what order were they declared?".
 */
data class AppliedEnrichment(
    val enrichmentId: String,
    val enrichmentName: String,
    val derivedKeys: Set<String>,
)
