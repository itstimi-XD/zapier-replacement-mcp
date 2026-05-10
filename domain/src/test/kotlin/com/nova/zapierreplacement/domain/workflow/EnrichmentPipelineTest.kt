package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EnrichmentPipelineTest {

    @Test
    fun `applies enrichments in declaration order and accumulates derived fields`() {
        val pipeline = EnrichmentPipeline(
            listOf(
                enrichment("e1") { mapOf("country_code" to "US") },
                enrichment("e2") { mapOf("currency" to "USD") },
                enrichment("e3") { mapOf("locale" to "en-US") },
            ),
        )

        val result = pipeline.enrich(event(payload = mapOf("user_id" to 42)))

        assertEquals(
            mapOf(
                "user_id" to 42,
                "country_code" to "US",
                "currency" to "USD",
                "locale" to "en-US",
            ),
            result.event.payload,
        )
        assertEquals(listOf("e1", "e2", "e3"), result.applied.map { it.enrichmentId })
    }

    @Test
    fun `later enrichment sees earlier enrichments' output`() {
        // The whole point of the pattern: build derivations on top of
        // previous derivations without round-tripping through a new Zap.
        // (In production the numeric type comes from the event payload
        // parser — verify your adapter normalizes Int vs Long before
        // wiring real enrichments to a JSON source.)
        val pipeline = EnrichmentPipeline(
            listOf(
                enrichment("price") { mapOf("price_usd" to 100) },
                enrichment("tax") { p ->
                    val price = p["price_usd"] as Int
                    mapOf("tax_usd" to price / 10)
                },
                enrichment("total") { p ->
                    val price = p["price_usd"] as Int
                    val tax = p["tax_usd"] as Int
                    mapOf("total_usd" to price + tax)
                },
            ),
        )

        val result = pipeline.enrich(event(payload = emptyMap()))

        assertEquals(110, result.event.payload["total_usd"])
    }

    @Test
    fun `enrichment can override a key present in the original event payload`() {
        // Documents the cross-source conflict policy: original-payload
        // keys are not protected; enrichments can replace them. Same
        // later-wins rule, just across the original-vs-derived boundary.
        val pipeline = EnrichmentPipeline(
            listOf(enrichment("normalize-status") { mapOf("status" to "submitted") }),
        )

        val result = pipeline.enrich(event(payload = mapOf("status" to "draft", "user_id" to 1)))

        assertEquals("submitted", result.event.payload["status"])
        // Untouched keys from the original payload pass through.
        assertEquals(1, result.event.payload["user_id"])
        // originalPayload still has the pre-override value for audit.
        assertEquals("draft", result.originalPayload["status"])
    }

    @Test
    fun `later enrichment overrides earlier on key conflict`() {
        // Documented policy: later wins. Tests exist precisely to pin
        // the conflict semantics so a future refactor can't quietly
        // flip them.
        val pipeline = EnrichmentPipeline(
            listOf(
                enrichment("first") { mapOf("status" to "draft") },
                enrichment("second") { mapOf("status" to "submitted") },
            ),
        )

        val result = pipeline.enrich(event(payload = emptyMap()))

        assertEquals("submitted", result.event.payload["status"])
    }

    @Test
    fun `originalPayload preserves the input regardless of derivations`() {
        val pipeline = EnrichmentPipeline(
            listOf(enrichment("e1") { mapOf("derived" to true) }),
        )
        val originalPayload = mapOf("user_id" to 1, "amount" to 50)

        val result = pipeline.enrich(event(payload = originalPayload))

        assertEquals(originalPayload, result.originalPayload)
        assertEquals(true, result.event.payload["derived"])
        // The original map reference must not be mutated.
        assertEquals(2, originalPayload.size)
    }

    @Test
    fun `applied trail records which enrichment produced which keys`() {
        val pipeline = EnrichmentPipeline(
            listOf(
                enrichment("identity") { mapOf("country_code" to "US", "currency" to "USD") },
                enrichment("locale") { mapOf("locale" to "en-US") },
            ),
        )

        val result = pipeline.enrich(event(payload = emptyMap()))

        val identityRecord = result.applied.first { it.enrichmentId == "identity" }
        val localeRecord = result.applied.first { it.enrichmentId == "locale" }
        assertEquals(setOf("country_code", "currency"), identityRecord.derivedKeys)
        assertEquals(setOf("locale"), localeRecord.derivedKeys)
    }

    @Test
    fun `enrichment that returns empty map is a legal no-op`() {
        val pipeline = EnrichmentPipeline(
            listOf(
                enrichment("noop") { emptyMap() },
                enrichment("real") { mapOf("k" to "v") },
            ),
        )

        val result = pipeline.enrich(event(payload = emptyMap()))

        val noopRecord = result.applied.first { it.enrichmentId == "noop" }
        assertTrue(noopRecord.derivedKeys.isEmpty())
        assertEquals("v", result.event.payload["k"])
    }

    @Test
    fun `empty pipeline returns the event payload unchanged`() {
        val pipeline = EnrichmentPipeline(emptyList())
        val originalPayload = mapOf("k" to "v")

        val result = pipeline.enrich(event(payload = originalPayload))

        assertEquals(originalPayload, result.event.payload)
        assertTrue(result.applied.isEmpty())
    }

    @Test
    fun `rejects construction with duplicate enrichment ids`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            EnrichmentPipeline(
                listOf(
                    enrichment("dup") { emptyMap() },
                    enrichment("dup") { emptyMap() },
                ),
            )
        }
        assertTrue(ex.message!!.contains("dup"))
    }

    @Test
    fun `mutating the input list after construction does not affect the pipeline`() {
        val mutable = mutableListOf(enrichment("only") { mapOf("k" to "original") })
        val pipeline = EnrichmentPipeline(mutable)

        mutable.clear()
        mutable += enrichment("injected") { mapOf("k" to "injected") }

        val result = pipeline.enrich(event(payload = emptyMap()))

        assertEquals("original", result.event.payload["k"])
        assertEquals(listOf("only"), result.applied.map { it.enrichmentId })
    }

    @Test
    fun `event id and type pass through unchanged`() {
        val pipeline = EnrichmentPipeline(
            listOf(enrichment("e") { mapOf("k" to "v") }),
        )

        val result = pipeline.enrich(
            WorkflowEvent(id = "evt-42", type = "lead.created", payload = emptyMap()),
        )

        assertEquals("evt-42", result.event.id)
        assertEquals("lead.created", result.event.type)
    }

    private fun enrichment(
        id: String,
        derive: (Map<String, Any?>) -> Map<String, Any?>,
    ) = EventEnrichment(
        id = id,
        name = "enrichment-$id",
        derive = derive,
    )

    private fun event(payload: Map<String, Any?>) = WorkflowEvent(
        id = "evt-test",
        type = "test",
        payload = payload,
    )
}
