package com.nova.zapierreplacement.application.workflow

import com.nova.zapierreplacement.application.ports.AsyncRequestRegistryPort
import com.nova.zapierreplacement.domain.workflow.AsyncRequestState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AcceptAsyncRequestUseCaseTest {

    @Test
    fun `accepts a request and persists it as Accepted with the generated id, timestamp, and payload`() = runTest {
        val registry = RecordingRegistry()

        val request = AcceptAsyncRequestUseCase(
            registry = registry,
            idGenerator = { "req-fixed" },
            clock = { 12_345 },
        ).execute(payload = mapOf("event" to "lead.created", "lead_id" to 42))

        assertEquals("req-fixed", request.id)
        assertEquals(12_345L, request.acceptedAtEpochMillis)
        assertEquals(mapOf("event" to "lead.created", "lead_id" to 42), request.payload)
        // The state persisted must be Accepted, with the same request value.
        val persisted = assertIs<AsyncRequestState.Accepted>(registry.persisted.single())
        assertEquals(request, persisted.request)
    }

    @Test
    fun `payload is defensively copied so external mutation cannot alter the persisted state`() = runTest {
        val registry = RecordingRegistry()
        val mutablePayload = mutableMapOf<String, Any?>("event" to "lead.created")

        val request = AcceptAsyncRequestUseCase(
            registry = registry,
            idGenerator = { "req-fixed" },
            clock = { 0 },
        ).execute(payload = mutablePayload)

        mutablePayload["event"] = "tampered"

        assertEquals("lead.created", request.payload["event"])
    }

    @Test
    fun `default payload is empty when caller does not supply one`() = runTest {
        val registry = RecordingRegistry()

        val request = AcceptAsyncRequestUseCase(
            registry = registry,
            idGenerator = { "req-empty" },
            clock = { 0 },
        ).execute()

        assertEquals(emptyMap(), request.payload)
    }

    @Test
    fun `id and timestamp are taken from the injected generators each call`() = runTest {
        // Ensures the use case isn't holding onto stale values across calls.
        val ids = mutableListOf("req-1", "req-2", "req-3")
        val times = mutableListOf(100L, 200L, 300L)
        val registry = RecordingRegistry()

        val uc = AcceptAsyncRequestUseCase(
            registry = registry,
            idGenerator = { ids.removeAt(0) },
            clock = { times.removeAt(0) },
        )

        val a = uc.execute()
        val b = uc.execute()
        val c = uc.execute()

        assertEquals(listOf("req-1", "req-2", "req-3"), listOf(a.id, b.id, c.id))
        assertEquals(listOf(100L, 200L, 300L), listOf(a.acceptedAtEpochMillis, b.acceptedAtEpochMillis, c.acceptedAtEpochMillis))
        assertEquals(3, registry.persisted.size)
    }

    @Test
    fun `registry failure surfaces to the caller and the request is not returned`() = runTest {
        // The acceptance side either persists or it doesn't — there is
        // no "half accepted" tier. If the registry throws, the caller
        // (API layer) must respond with a 5xx so the original sender
        // can retry, NOT a 202 with a request id that nothing knows about.
        val registry = ThrowingRegistry()

        val ex = assertFailsWith<IllegalStateException> {
            AcceptAsyncRequestUseCase(
                registry = registry,
                idGenerator = { "req-x" },
                clock = { 0 },
            ).execute()
        }
        assertEquals("registry persist failed", ex.message)
    }

    private class RecordingRegistry : AsyncRequestRegistryPort {
        val persisted = mutableListOf<AsyncRequestState.Accepted>()
        override suspend fun persistAccepted(state: AsyncRequestState.Accepted) {
            persisted += state
        }
        override suspend fun load(requestId: String): AsyncRequestState? = null
        override suspend fun compareAndUpdate(
            expectedCurrent: AsyncRequestState,
            newState: AsyncRequestState,
        ): Boolean = error("compareAndUpdate() should not be called by AcceptAsyncRequestUseCase")
    }

    private class ThrowingRegistry : AsyncRequestRegistryPort {
        override suspend fun persistAccepted(state: AsyncRequestState.Accepted) {
            throw IllegalStateException("registry persist failed")
        }
        override suspend fun load(requestId: String): AsyncRequestState? = null
        override suspend fun compareAndUpdate(
            expectedCurrent: AsyncRequestState,
            newState: AsyncRequestState,
        ): Boolean = error("compareAndUpdate() should not be called by AcceptAsyncRequestUseCase")
    }
}
