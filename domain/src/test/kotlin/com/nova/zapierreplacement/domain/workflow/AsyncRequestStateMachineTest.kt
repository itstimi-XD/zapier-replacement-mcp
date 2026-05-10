package com.nova.zapierreplacement.domain.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AsyncRequestStateMachineTest {

    private val request = AsyncRequest(id = "req-1", acceptedAtEpochMillis = 1_000)

    @Test
    fun `Accepted transitions to Running with the worker's start timestamp`() {
        val accepted = AsyncRequestState.Accepted(request)

        val running = AsyncRequestStateMachine.markRunning(accepted, startedAtEpochMillis = 2_000)

        assertEquals(request, running.request)
        assertEquals(2_000L, running.startedAtEpochMillis)
    }

    @Test
    fun `Running transitions to Completed carrying the result payload`() {
        val running = AsyncRequestState.Running(request, startedAtEpochMillis = 2_000)

        val completed = AsyncRequestStateMachine.markCompleted(
            current = running,
            completedAtEpochMillis = 3_000,
            result = mapOf("status" to "ok", "rows" to 42),
        )

        assertEquals(request, completed.request)
        assertEquals(3_000L, completed.completedAtEpochMillis)
        assertEquals(mapOf("status" to "ok", "rows" to 42), completed.result)
    }

    @Test
    fun `Accepted can transition directly to Failed when work is rejected before starting`() {
        // The "validation failed at worker pickup" case: never ran, but
        // we still want the terminal Failed state for caller observability.
        val accepted = AsyncRequestState.Accepted(request)

        val failed = AsyncRequestStateMachine.markFailed(
            current = accepted,
            failedAtEpochMillis = 2_500,
            reason = "validation failed",
        )

        assertEquals("validation failed", failed.reason)
    }

    @Test
    fun `Running can transition to Failed when work fails mid-execution`() {
        val running = AsyncRequestState.Running(request, startedAtEpochMillis = 2_000)

        val failed = AsyncRequestStateMachine.markFailed(
            current = running,
            failedAtEpochMillis = 2_700,
            reason = "external API timeout",
        )

        assertEquals("external API timeout", failed.reason)
    }

    @Test
    fun `markCompleted defends against being called from non-Running states`() {
        val accepted = AsyncRequestState.Accepted(request)
        val ex = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markCompleted(
                current = accepted,
                completedAtEpochMillis = 3_000,
                result = emptyMap(),
            )
        }
        // The error message must point at both the current state and
        // the request id so a production diagnostic isn't a guessing game.
        assertEquals(true, ex.message?.contains("Accepted"))
        assertEquals(true, ex.message?.contains("req-1"))
    }

    @Test
    fun `markCompleted from a terminal state is rejected`() {
        val completed = AsyncRequestState.Completed(
            request = request,
            completedAtEpochMillis = 3_000,
            result = mapOf("status" to "ok"),
        )
        assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markCompleted(
                current = completed,
                completedAtEpochMillis = 4_000,
                result = mapOf("status" to "double-completed"),
            )
        }
    }

    @Test
    fun `markRunning from a non-Accepted state is rejected with a diagnostic message`() {
        val running = AsyncRequestState.Running(request, startedAtEpochMillis = 2_000)
        val fromRunning = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markRunning(running, startedAtEpochMillis = 2_500)
        }
        assertEquals(true, fromRunning.message?.contains("Running"))
        assertEquals(true, fromRunning.message?.contains("req-1"))

        val failed = AsyncRequestState.Failed(
            request = request,
            failedAtEpochMillis = 2_500,
            reason = "x",
        )
        val fromFailed = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markRunning(failed, startedAtEpochMillis = 3_000)
        }
        assertEquals(true, fromFailed.message?.contains("Failed"))
        assertEquals(true, fromFailed.message?.contains("req-1"))

        // Completed → markRunning closes the transition matrix gap; a
        // terminal state must never be lifted back into Running.
        val completed = AsyncRequestState.Completed(
            request = request,
            completedAtEpochMillis = 3_000,
            result = emptyMap(),
        )
        val fromCompleted = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markRunning(completed, startedAtEpochMillis = 4_000)
        }
        assertEquals(true, fromCompleted.message?.contains("Completed"))
        assertEquals(true, fromCompleted.message?.contains("req-1"))
    }

    @Test
    fun `markFailed from terminal states is rejected with a diagnostic message`() {
        // Both Completed and Failed are terminal. Re-entering Failed
        // from a terminal state would erase the original outcome.
        val completed = AsyncRequestState.Completed(
            request = request,
            completedAtEpochMillis = 3_000,
            result = emptyMap(),
        )
        val fromCompleted = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markFailed(completed, failedAtEpochMillis = 4_000, reason = "x")
        }
        assertEquals(true, fromCompleted.message?.contains("Completed"))
        assertEquals(true, fromCompleted.message?.contains("req-1"))

        val failed = AsyncRequestState.Failed(
            request = request,
            failedAtEpochMillis = 3_000,
            reason = "first failure",
        )
        val fromFailed = assertFailsWith<IllegalStateException> {
            AsyncRequestStateMachine.markFailed(failed, failedAtEpochMillis = 4_000, reason = "second failure")
        }
        assertEquals(true, fromFailed.message?.contains("Failed"))
        assertEquals(true, fromFailed.message?.contains("req-1"))
    }

    @Test
    fun `result map is defensively copied so external mutation cannot alter Completed state`() {
        val running = AsyncRequestState.Running(request, startedAtEpochMillis = 2_000)
        val mutableResult = mutableMapOf<String, Any?>("status" to "ok")

        val completed = AsyncRequestStateMachine.markCompleted(
            current = running,
            completedAtEpochMillis = 3_000,
            result = mutableResult,
        )

        mutableResult["status"] = "tampered"

        assertEquals("ok", completed.result["status"])
    }

    @Test
    fun `state types preserve their data via copy semantics`() {
        // Documents that the data classes can be copied for projection
        // (e.g. building a status DTO) without jumping through hoops.
        val running = AsyncRequestState.Running(request, startedAtEpochMillis = 2_000)
        val copied = running.copy(startedAtEpochMillis = 2_500)

        assertIs<AsyncRequestState.Running>(copied)
        assertEquals(2_500L, copied.startedAtEpochMillis)
        assertEquals(request, copied.request)
    }
}
