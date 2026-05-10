package com.nova.zapierreplacement.domain.workflow

/**
 * Pure state-transition logic for [AsyncRequestState].
 *
 * Lives in the domain so the legal-move set is a single source of
 * truth — every adapter / use case that updates a request's state
 * goes through this object, and a future regression that, say, lets
 * a `Completed` go back to `Running` would have to first defeat the
 * tests that pin the rejected transitions.
 *
 * Stateless, side-effect free; the persistence layer is responsible
 * for reading the current state, calling a transition here, and
 * writing the result back atomically.
 *
 * Declared as `object` because the transitions carry no per-instance
 * state and are referentially transparent. There is no reason to
 * instantiate more than one, and `object` makes that explicit — sibling
 * stateless logic helpers in this codebase should follow the same
 * convention.
 */
object AsyncRequestStateMachine {

    /** Accepted → Running. Any other origin throws. */
    fun markRunning(
        current: AsyncRequestState,
        startedAtEpochMillis: Long,
    ): AsyncRequestState.Running {
        check(current is AsyncRequestState.Accepted) {
            "Cannot mark Running: current state is ${current::class.simpleName} for request ${current.request.id}."
        }
        return AsyncRequestState.Running(
            request = current.request,
            startedAtEpochMillis = startedAtEpochMillis,
        )
    }

    /** Running → Completed. Any other origin throws. */
    fun markCompleted(
        current: AsyncRequestState,
        completedAtEpochMillis: Long,
        result: Map<String, Any?>,
    ): AsyncRequestState.Completed {
        check(current is AsyncRequestState.Running) {
            "Cannot mark Completed: current state is ${current::class.simpleName} for request ${current.request.id}. " +
                "Completion must be preceded by Running."
        }
        return AsyncRequestState.Completed(
            request = current.request,
            completedAtEpochMillis = completedAtEpochMillis,
            result = result.toMap(),
        )
    }

    /**
     * (Accepted | Running) → Failed.
     *
     * Both origins are legal because work can be rejected before it
     * ever starts (e.g. validation failure on a message the worker
     * pulls off the queue) or fail mid-execution. Going to Failed from
     * an already-terminal state (Completed, Failed) is rejected.
     */
    fun markFailed(
        current: AsyncRequestState,
        failedAtEpochMillis: Long,
        reason: String,
    ): AsyncRequestState.Failed {
        check(current is AsyncRequestState.Accepted || current is AsyncRequestState.Running) {
            "Cannot mark Failed: current state is ${current::class.simpleName} for request ${current.request.id}. " +
                "Failure must be preceded by Accepted or Running, not a terminal state."
        }
        return AsyncRequestState.Failed(
            request = current.request,
            failedAtEpochMillis = failedAtEpochMillis,
            reason = reason,
        )
    }
}
