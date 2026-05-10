package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardedPipelineTest {

    @Test
    fun `accepts steps with unique IDs and preserves declaration order`() {
        val steps = listOf(step("a"), step("b"), step("c"))
        val pipeline = GuardedPipeline(
            gate = passingGate(),
            steps = steps,
        )

        assertEquals(listOf("a", "b", "c"), pipeline.steps.map { it.id })
    }

    @Test
    fun `rejects construction with duplicate step ids`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            GuardedPipeline(
                gate = passingGate(),
                steps = listOf(step("dup"), step("dup")),
            )
        }
        assertTrue(ex.message!!.contains("dup"))
    }

    @Test
    fun `accepts a pipeline with zero steps`() {
        // A guard with no body is unusual but legal; it means "evaluate the
        // predicate for its side-effect-free observability value." Keep
        // construction permissive — runtime can warn if it wants to.
        val pipeline = GuardedPipeline(gate = passingGate(), steps = emptyList())

        assertTrue(pipeline.steps.isEmpty())
    }

    @Test
    fun `mutating the input list after construction does not affect the pipeline`() {
        val mutable = mutableListOf(step("only"))
        val pipeline = GuardedPipeline(gate = passingGate(), steps = mutable)

        mutable.clear()
        mutable += step("injected")

        assertEquals(listOf("only"), pipeline.steps.map { it.id })
    }

    @Test
    fun `gate is exposed as a first-class field rather than smuggled into steps`() {
        // Documents the type-level contract the pattern exists for: callers
        // never have to reason about "is the first step the filter?" because
        // the gate has its own slot.
        val gate = GuardCondition(id = "g1", description = "country=US") { true }
        val pipeline = GuardedPipeline(gate = gate, steps = listOf(step("a")))

        assertEquals("g1", pipeline.gate.id)
        assertEquals("country=US", pipeline.gate.description)
    }

    private fun step(id: String) = PipelineStep(
        id = id,
        name = "step-$id",
        targetChannel = NotificationChannel.SLACK,
    )

    private fun passingGate() = GuardCondition(
        id = "default-gate",
        description = "always passes",
        predicate = { true },
    )
}
