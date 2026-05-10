package com.nova.zapierreplacement.domain.workflow

/**
 * Lifecycle state of an [AsyncRequest].
 *
 * The state space is *closed* — these four are the only legal values.
 * Adding a new state requires touching every consumer of this type
 * (compiler enforced via the `sealed` keyword), which is exactly the
 * property we want when downstream behavior depends on the state.
 *
 * Transitions are not free-form: only the moves enforced by
 * [AsyncRequestStateMachine] are legal. Going from Accepted → Running
 * → (Completed | Failed) is the happy path; Accepted → Failed is also
 * valid (the work was rejected before it ever started). All other
 * transitions throw [IllegalStateException]. Encoding this as runtime
 * checks rather than type-level "next" methods is intentional — the
 * persistence layer reads a row from a database and reconstructs the
 * current state at runtime, so a free-form transition function fits
 * the actual call shape.
 */
sealed interface AsyncRequestState {

    val request: AsyncRequest

    /** Request was received and persisted; work has not started. */
    data class Accepted(override val request: AsyncRequest) : AsyncRequestState

    /** Work is in progress; [startedAtEpochMillis] is when the worker picked it up. */
    data class Running(
        override val request: AsyncRequest,
        val startedAtEpochMillis: Long,
    ) : AsyncRequestState

    /**
     * Work finished successfully; [result] is the payload to return on
     * callback / poll. Values must be JSON-serializable primitives,
     * collections, or null — anything that survives a round-trip
     * through the registry's serialization. Putting domain objects or
     * lambdas here will fail at the infrastructure boundary.
     */
    data class Completed(
        override val request: AsyncRequest,
        val completedAtEpochMillis: Long,
        val result: Map<String, Any?>,
    ) : AsyncRequestState

    /** Work failed; [reason] is a short human-readable diagnostic for the caller. */
    data class Failed(
        override val request: AsyncRequest,
        val failedAtEpochMillis: Long,
        val reason: String,
    ) : AsyncRequestState
}
