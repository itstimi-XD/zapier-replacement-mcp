package com.nova.zapierreplacement.application.ports

import com.nova.zapierreplacement.domain.workflow.AsyncRequestState

/**
 * Outbound port for persisting [AsyncRequestState] across the lifecycle
 * of an asynchronously-processed request.
 *
 * The acceptance side of the synchronous-webhook-chain replacement:
 * the inbound API persists `Accepted` here, returns the request id to
 * the original sender immediately, and a separate worker / scheduler
 * later loads the state, transitions it (via
 * [com.nova.zapierreplacement.domain.workflow.AsyncRequestStateMachine]),
 * and saves the new state via the atomic [compareAndUpdate].
 *
 * Implementations live in :infrastructure (Postgres `UPDATE ... WHERE
 * version = ?`, Redis Lua scripts, etc.). The contract is that the
 * port — not the caller — owns atomicity; clients of this interface
 * never need to coordinate locks themselves.
 */
interface AsyncRequestRegistryPort {

    /**
     * Persist the initial [AsyncRequestState.Accepted] for a brand-new
     * request. Throws if the request id is already present — callers
     * must generate collision-resistant ids upstream
     * (see [com.nova.zapierreplacement.domain.workflow.AsyncRequest.id]).
     */
    suspend fun persistAccepted(state: AsyncRequestState.Accepted)

    /** Load the current state for a known request id, or null if unknown. */
    suspend fun load(requestId: String): AsyncRequestState?

    /**
     * Atomically replace the on-disk state with [newState], conditional
     * on the on-disk state currently equalling [expectedCurrent].
     *
     * @return `true` if the swap succeeded, `false` if the on-disk
     *         state did not match [expectedCurrent] (concurrent worker
     *         won the race). Callers must treat `false` as a conflict
     *         and decide whether to retry the load-transition-write
     *         cycle, drop the work, or escalate.
     *
     * Encoding the pre-state in the signature is deliberate — a
     * `update(newState)` shape would let implementers split the check
     * from the set and a non-atomic adapter would compile cleanly.
     */
    suspend fun compareAndUpdate(
        expectedCurrent: AsyncRequestState,
        newState: AsyncRequestState,
    ): Boolean
}
