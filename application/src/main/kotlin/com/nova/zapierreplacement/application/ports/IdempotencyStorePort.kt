package com.nova.zapierreplacement.application.ports

/**
 * Outbound port for the idempotency check that protects webhook receivers
 * from double-processing retried/duplicated deliveries.
 *
 * The contract is *atomic* check-and-set: a naïve `contains` followed by
 * a separate `add` admits a TOCTOU race under concurrent deliveries from
 * the same provider. Implementations live in :infrastructure (typically
 * Postgres `INSERT ... ON CONFLICT DO NOTHING` or Redis `SETNX`).
 */
interface IdempotencyStorePort {
    /**
     * Atomically records [key] if it is not already present.
     *
     * @return `true` if this call inserted [key] (i.e. it is the first
     *         time we have seen this delivery and the caller should
     *         proceed with side effects), `false` if [key] was already
     *         present and the caller must short-circuit.
     *
     * Callers are not expected to roll back a key on downstream failure
     * (e.g. dispatcher exception) — see the policy comment in
     * [com.nova.zapierreplacement.application.workflow.ReceiveWebhookEventUseCase].
     * Implementations therefore do not need a paired delete operation.
     */
    suspend fun markSeenIfAbsent(key: String): Boolean
}
