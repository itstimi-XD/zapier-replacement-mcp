package com.nova.zapierreplacement.domain.workflow

/**
 * Composes a chain of [EventEnrichment]s and applies them in order to a
 * single [WorkflowEvent]'s payload.
 *
 * Each enrichment sees the cumulative payload — fields produced by
 * earlier enrichments are visible to later ones. This mirrors the
 * "stacked Code steps" Zapier setup the pattern replaces, except
 * everything happens in-process with zero task cost, zero timeout,
 * and full unit-test reach.
 *
 * Conflict policy: when two enrichments produce the same key, the
 * *later* one wins. **Deliberate default:** throwing on conflicts
 * would make enrichment ordering load-bearing in subtle ways and turn
 * harmless additions into break-the-build events. Callers that need
 * strict no-overlap guarantees should validate enrichment outputs in
 * tests, not at runtime.
 *
 * Enrichment IDs must be unique and non-blank so callers can correlate
 * per-step output back to its source — same invariant as
 * [SequentialPipeline].
 *
 * No `EnrichEventUseCase` wrapper exists by design: enrichment is a
 * pure building block meant to compose *inside* other use cases (e.g.
 * between an inbound webhook and a downstream router), not orchestrate
 * independently. Add an application-layer wrapper only when a concrete
 * observability or cross-cutting concern cannot live in the composing
 * use case.
 */
class EnrichmentPipeline(enrichments: List<EventEnrichment>) {

    init {
        val duplicates = enrichments.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) {
            "Enrichment IDs must be unique. Duplicates: $duplicates"
        }
        val blanks = enrichments.filter { it.id.isBlank() }
        require(blanks.isEmpty()) {
            "Enrichment IDs must not be blank."
        }
    }

    /** Defensive copy so external mutation can't change the pipeline after construction. */
    val enrichments: List<EventEnrichment> = enrichments.toList()

    fun enrich(event: WorkflowEvent): EnrichedEvent {
        var payload: Map<String, Any?> = event.payload
        val applied = mutableListOf<AppliedEnrichment>()

        for (enrichment in enrichments) {
            val derived = enrichment.derive(payload)
            payload = payload + derived
            applied += AppliedEnrichment(
                enrichmentId = enrichment.id,
                enrichmentName = enrichment.name,
                derivedKeys = derived.keys.toSet(),
            )
        }

        return EnrichedEvent(
            event = WorkflowEvent(id = event.id, type = event.type, payload = payload),
            originalPayload = event.payload,
            applied = applied.toList(),
        )
    }
}
