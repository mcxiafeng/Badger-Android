package top.mcxiafeng.badger.sync

interface OutboxQueue {
    
    suspend fun enqueue(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: kotlinx.serialization.json.JsonObject,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    ): OutboxEnqueueResult

    
    suspend fun cancelEntity(entityKind: EntityKind, localId: Long): Int

    
    suspend fun getReady(
        limit: Int = 20,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
        includeBackoff: Boolean = false,
    ): List<OutboxOp>

    
    suspend fun markSuccess(outboxId: Long)

    
    suspend fun recordFailure(outboxId: Long, error: Throwable, now: Long = top.mcxiafeng.badger.shared.util.nowMs())

    
    suspend fun backfillAfterCreate(
        entityKind: EntityKind,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    )
}

sealed interface OutboxEnqueueResult {
    
    data class Created(val outboxId: Long) : OutboxEnqueueResult

    
    data class MergedIntoExisting(val outboxId: Long) : OutboxEnqueueResult

    
    data object IgnoredDuplicateCreate : OutboxEnqueueResult
}
