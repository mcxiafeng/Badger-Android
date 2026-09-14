package top.mcxiafeng.badger.sync

import top.mcxiafeng.badger.shared.db.dbTransaction
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity
import top.mcxiafeng.badger.shared.util.nowMs

class OutboxStore(private val database: AppDatabase) : OutboxQueue {

    private val dao: OutboxDao = database.outboxDao()

    override suspend fun enqueue(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: JsonObject,
        now: Long,
    ): OutboxEnqueueResult {
        require(localId > 0) { "localId must be a real rowId, got $localId" }
        val mergeKey = if (op == OutboxOpType.CREATE || op == OutboxOpType.PATCH) {
            "${entityKind.name}:$localId:${op.name}"
        } else {
            null
        }
        return database.dbTransaction {
            if (op == OutboxOpType.DELETE) {
                val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
                if (cancelled > 0) {
                    BadgerLog.d(TAG, "enqueue DELETE: cancelled $cancelled unsent CREATE/PATCH kind=${entityKind.name} localId=$localId")
                }
            }
            
            
            
            
            
            val existing = mergeKey?.let { dao.getByMergeKey(it) }
            if (existing != null) {
                mergeOrIgnore(entityKind, localId, remoteId, op, mergeKey, payload, now)
            } else {
                val inserted = dao.insertOrAbort(newRow(entityKind, localId, remoteId, op, mergeKey, payload, now))
                BadgerLog.d(TAG, "enqueue: created id=$inserted kind=${entityKind.name} localId=$localId op=${op.name}")
                OutboxEnqueueResult.Created(inserted)
            }
        }
    }

    

    override suspend fun getReady(
        limit: Int,
        now: Long,
        includeBackoff: Boolean,
    ): List<OutboxOp> {
        require(limit > 0) { "limit must be positive" }
        val rows = if (includeBackoff) dao.getReadyIncludingBackoff(limit) else dao.getReady(now, limit)
        return rows.map { entity ->
            OutboxOp(
                id = entity.id,
                entityKind = EntityKind.valueOf(entity.entityKind),
                localId = entity.localId,
                remoteId = entity.remoteId,
                op = OutboxOpType.valueOf(entity.op),
                payload = parsePayload(entity.payloadJson),
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                attempts = entity.attempts,
                nextAttemptAt = entity.nextAttemptAt,
                lastError = entity.lastError,
            )
        }
    }

    

    override suspend fun markSuccess(outboxId: Long) {
        val deleted = dao.deleteById(outboxId)
        if (deleted == 0) {
            BadgerLog.d(TAG, "markSuccess: id=$outboxId 行已换代/不存在，保留新代 payload")
        }
    }

    

    override suspend fun backfillAfterCreate(
        entityKind: EntityKind,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long,
    ) {
        if (oldRemoteId == newRemoteId) return
        val rows = dao.backfillRemoteId(entityKind.name, localId, oldRemoteId, newRemoteId, now)
        var memberFixed = 0
        if (entityKind == EntityKind.PERSON) {
            
            val needle = "%\"$oldRemoteId\"%"
            dao.getMemberRowsReferencing(needle).forEach { row ->
                try {
                    val payload = parsePayload(row.payloadJson)
                    val personUuid = (payload["personUuid"] as? JsonPrimitive)?.content
                    if (personUuid == oldRemoteId) {
                        val updated = JsonObject(payload + ("personUuid" to JsonPrimitive(newRemoteId)))
                        dao.updatePayloadJson(row.id, updated.toString(), now)
                        memberFixed++
                    }
                } catch (e: Exception) {
                    BadgerLog.e(TAG, "backfillAfterCreate: MEMBER payload 解析失败 id=${row.id}", e)
                }
            }
        }
        BadgerLog.d(
            TAG,
            "backfillAfterCreate: kind=${entityKind.name} localId=$localId rows=$rows memberPayloads=$memberFixed " +
                "old=${oldRemoteId.take(8)} new=${newRemoteId.take(8)}",
        )
    }

    

    override suspend fun cancelEntity(entityKind: EntityKind, localId: Long): Int {
        val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
        if (cancelled > 0) {
            BadgerLog.d(TAG, "cancelEntity: kind=${entityKind.name} localId=$localId cancelled=$cancelled")
        }
        return cancelled
    }

    

    override suspend fun recordFailure(outboxId: Long, error: Throwable, now: Long) {
        val message = error.message?.take(MAX_LAST_ERROR_LENGTH) ?: error::class.simpleName ?: "Exception"
        val updated = dao.recordFailure(outboxId, now, BACKOFF_BASE_MILLIS, MAX_BACKOFF_EXPONENT, message)
        if (updated == 0) {
            BadgerLog.d(TAG, "recordFailure: id=$outboxId 行已换代/不存在，attempts 不跨代累计")
        } else {
            BadgerLog.d(TAG, "recordFailure: id=$outboxId attempts=$updated error=$message")
        }
    }

    
    private suspend fun mergeOrIgnore(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        mergeKey: String?,
        payload: JsonObject,
        now: Long,
    ): OutboxEnqueueResult {
        if (op != OutboxOpType.PATCH) {
            BadgerLog.d(TAG, "enqueue: CREATE 已在队被忽略 kind=${entityKind.name} localId=$localId")
            return OutboxEnqueueResult.IgnoredDuplicateCreate
        }
        val existing = dao.getByMergeKey(mergeKey!!) ?: run {
            BadgerLog.e(TAG, "enqueue: mergeKey=$mergeKey 行消失，按新建处理")
            return OutboxEnqueueResult.Created(dao.insertOrAbort(newRow(entityKind, localId, remoteId, op, mergeKey, payload, now)))
        }
        val merged = mergePayload(existing.payloadJson, payload)
        val newRemoteId = remoteId?.takeIf { it.isNotBlank() } ?: existing.remoteId
        dao.deleteById(existing.id)
        val row = newRow(entityKind, localId, newRemoteId, op, mergeKey, merged, now)
            .copy(createdAt = existing.createdAt)
        val newId = dao.insertOrAbort(row)
        BadgerLog.d(TAG, "enqueue: merged kind=${entityKind.name} localId=$localId op=PATCH oldId=${existing.id} -> newId=$newId")
        return OutboxEnqueueResult.MergedIntoExisting(newId)
    }

    
    private fun mergePayload(existingJson: String, incoming: JsonObject): JsonObject {
        val existing = parsePayload(existingJson)
        val overrides = incoming.filterValues { it !is JsonNull }
        return JsonObject(existing + overrides)
    }

    private fun newRow(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        mergeKey: String?,
        payload: JsonObject,
        now: Long,
    ): OutboxEntity = OutboxEntity(
        entityKind = entityKind.name,
        localId = localId,
        remoteId = remoteId,
        op = op.name,
        mergeKey = mergeKey,
        payloadJson = payload.toString(),
        createdAt = now,
        updatedAt = now,
        nextAttemptAt = now,
    )

    private fun parsePayload(json: String): JsonObject = runCatching {
        BadgerJson.parseToJsonElement(json) as JsonObject
    }.getOrElse { e ->
        BadgerLog.e(TAG, "parsePayload: payloadJson 解析失败，按空 payload 处理", e)
        JsonObject(emptyMap())
    }

    companion object {
        private const val TAG = "OutboxStore"

        
        const val DEFAULT_BATCH = 50

        
        const val BACKOFF_BASE_MILLIS = 10_000L

        
        const val MAX_BACKOFF_EXPONENT = 6
        const val MAX_BACKOFF_MILLIS = 640_000L

        
        const val MAX_LAST_ERROR_LENGTH = 500
    }
}
