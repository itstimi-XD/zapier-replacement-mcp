package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SequentialPipelineTest {

    @Test
    fun `accepts steps with unique IDs and preserves declaration order`() {
        val steps = listOf(
            PipelineStep("z", "step-z", NotificationChannel.EMAIL),
            PipelineStep("a", "step-a", NotificationChannel.SMS),
            PipelineStep("m", "step-m", NotificationChannel.SLACK),
        )

        val pipeline = SequentialPipeline(steps)

        assertEquals(listOf("z", "a", "m"), pipeline.steps.map { it.id })
    }

    @Test
    fun `rejects steps with duplicate IDs`() {
        val steps = listOf(
            PipelineStep("dup", "first", NotificationChannel.EMAIL),
            PipelineStep("ok", "ok", NotificationChannel.SMS),
            PipelineStep("dup", "second", NotificationChannel.SLACK),
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            SequentialPipeline(steps)
        }

        assertTrue(ex.message!!.contains("dup"))
    }

    @Test
    fun `accepts empty step list`() {
        val pipeline = SequentialPipeline(emptyList())

        assertTrue(pipeline.steps.isEmpty())
    }

    @Test
    fun `defensive copy prevents external list mutation from affecting pipeline`() {
        val mutable = mutableListOf(
            PipelineStep("a", "a", NotificationChannel.SMS),
        )
        val pipeline = SequentialPipeline(mutable)

        mutable += PipelineStep("b", "b", NotificationChannel.EMAIL)

        assertEquals(1, pipeline.steps.size)
        assertEquals("a", pipeline.steps.single().id)
    }
}
