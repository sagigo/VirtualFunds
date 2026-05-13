package com.virtualfunds.data.util

import java.util.UUID

/**
 * Stable client-side UUIDs for an operation and its summary row (E2.3, E7.4).
 *
 * Both `operation_id` and the summary `transaction_id` act as idempotency anchors: if a
 * retry replays the same UUID, the RPC short-circuits (E7.4 idempotency rule). Callers
 * must generate these once per logical user action and reuse them across retries — the
 * easiest way to do that is to generate them at the start of a repository method and
 * pass them through.
 *
 * Mirrors `OperationIdGenerator` on desktop. Kotlin's `UUID.randomUUID()` is the same
 * Java implementation, so v4 random UUIDs are produced identically.
 */
internal object Ids {
    fun newOperationId(): String = UUID.randomUUID().toString()
    fun newTransactionId(): String = UUID.randomUUID().toString()
}
