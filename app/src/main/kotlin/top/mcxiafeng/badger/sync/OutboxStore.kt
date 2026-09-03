package top.mcxiafeng.badger.sync

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity

/**
 * Outbox op 类型（规格 §3.1）。
 *
 * - `CREATE` / `PATCH`：可合并 op，同 `(entityKind, localId)` 至多一行（靠 mergeKey 唯一索引认领）；
 * - `DELETE`：入队即取消同实体未发的 CREATE/PATCH；
 * - `MEMBER_ADD` / `MEMBER_REMOVE`：**不合并**，按 createdAt FIFO 逐条重放
 *   （成员子接口是独立幂等调用，add→remove→add 的中间态不可折叠）。
 */
enum class OutboxOpType {
    CREATE,
    PATCH,
    DELETE,
    MEMBER_ADD,
    MEMBER_REMOVE,
}

/**
 * enqueue 的类型化结果（不裸返 Boolean，调用方按结果记日志/触发 kick）。
 *
 * [归属] 这是 OutboxStore 的**认领结果**（首插/并入/忽略），不是仓库层提交结果——
 * 规格 §3.8 的 One-Version Rule 针对 Repository 的 `CommitResult`（T14/T54 复用），
 * 两者语义不同，不合并。
 */
sealed interface OutboxEnqueueResult {
    /** 新建了一行。 */
    data class Created(val outboxId: Long) : OutboxEnqueueResult

    /** 并入已有同键行并换代（返回换代后的新 outboxId，旧 id 的回执/失败记账随之失效）。 */
    data class MergedIntoExisting(val outboxId: Long) : OutboxEnqueueResult

    /** CREATE 已在队，忽略。幂等键已落盘；payload 变更不并入（差量走后续 PATCH）。 */
    data object IgnoredDuplicateCreate : OutboxEnqueueResult
}

/** OutboxStore 的公开读取模型（payload 已解析为 kotlinx [JsonObject]）。 */
data class OutboxOp(
    val id: Long,
    val entityKind: EntityKind,
    val localId: Long,
    val remoteId: String?,
    val op: OutboxOpType,
    val payload: JsonObject,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

/**
 * 通用 Outbox（规格 §3.1 / §3.8）：Person / Tag / Collection 远端写意图的持久队列。
 *
 * **合并语义**（替代旧 `pending_person_updates` 的 `serverId PK + CONFLICT_REPLACE`，F4 根治）：
 * - 同 `(kind, localId, PATCH)` 再入队 → **字段级 merge**：新 payload 非 null 字段覆盖，
 *   新 payload 缺省/null 的字段保留旧值（`updatePerson(name=null, …)` 的服务端语义是
 *   「不更新 name」而非「清空 name」）。
 * - 同 `(kind, localId, CREATE)` 再入队 → 忽略（决策，勿改）：CREATE 是幂等创建意图，
 *   payload 已变的内容不并入，差量一律走后续 PATCH，否则重复 POST 会覆盖服务端已收敛的状态。
 * - DELETE 入队 → 同事务内取消同实体未发的 CREATE/PATCH（没必要推一个马上要删的对象）。
 * - MEMBER_* → 不合并，FIFO（见 [OutboxOpType]）。
 *
 * **认领与换代**：
 * - 认领靠 `outbox.mergeKey` 唯一索引（`kind:localId:op`，CREATE/PATCH 行非 NULL）一次完成：
 *   INSERT-first，冲突才进 merge 分支；禁止 SELECT-then-INSERT 的 TOCTOU 写法。
 *   mergeKey 是规格中 `UNIQUE(entityKind, localId, op) WHERE op IN ('CREATE','PATCH')`
 *   部分唯一索引的 Room 等价实现（Room 注解不支持 WHERE，NULL 行不去重）。
 * - PATCH merge 通过 delete + reinsert **换代**：新行拿到新 outboxId、重置退避。
 *   Worker 持有的旧 outboxId 在 markSuccess/recordFailure 时按 id 落空（0 行），
 *   合并后的新 payload 不会被旧代成功回执误删，也不会被旧代失败记账污染。
 *
 * **结局与保留期**：行在成功前不删（成功 = markSuccess 物理删除），寿命必须盖过最长
 * 重试链（退避上限 [MAX_BACKOFF_MILLIS] × 无限轮）。超时/断连属于「未知结局」，
 * 同样只走 recordFailure 保留 PENDING 重试，禁止出现 FAILED_PERMANENT 类终态。
 *
 * 线程模型：阻塞式多语句事务（与旧 PendingPersonUpdateStore 同模式），
 * 调用方必须在 IO/Worker 线程；并发 enqueue/recordFailure 的原子性由
 * SQLite 事务 + 单条 UPDATE 保证。
 */
class OutboxStore(private val database: AppDatabase) {

    private val dao: OutboxDao = database.outboxDao()

    fun enqueue(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: JsonObject,
        now: Long = System.currentTimeMillis(),
    ): OutboxEnqueueResult {
        require(localId > 0) { "localId must be a real rowId, got $localId" }
        val mergeKey = if (op == OutboxOpType.CREATE || op == OutboxOpType.PATCH) {
            "${entityKind.name}:$localId:${op.name}"
        } else {
            null
        }
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            if (op == OutboxOpType.DELETE) {
                val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
                if (cancelled > 0) {
                    Log.d(TAG, "enqueue DELETE: cancelled $cancelled unsent CREATE/PATCH kind=${entityKind.name} localId=$localId")
                }
            }
            val inserted = tryInsert(newRow(entityKind, localId, remoteId, op, mergeKey, payload, now))
            if (inserted != null) {
                db.setTransactionSuccessful()
                Log.d(TAG, "enqueue: created id=$inserted kind=${entityKind.name} localId=$localId op=${op.name}")
                return OutboxEnqueueResult.Created(inserted)
            }
            val result = mergeOrIgnore(entityKind, localId, remoteId, op, mergeKey, payload, now)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 取「到期待重放」行，FIFO（createdAt → id 稳定序）。
     *
     * [includeBackoff]：手动「立即同步」（T17 syncOnce）传 true——无视退避窗口立即重试全部行；
     * WorkManager 触发的自动重放保持 false，尊重 recordFailure 的指数退避。
     */
    fun getReady(
        limit: Int = DEFAULT_BATCH,
        now: Long = System.currentTimeMillis(),
        includeBackoff: Boolean = false,
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

    /**
     * 成功出队。行 id 若已被后续 merge 换代（返回 0 行），说明旧代回执过期，
     * 新代 payload 留在队内等待重放——这是防丢编辑的关键路径，不是异常。
     */
    fun markSuccess(outboxId: Long) {
        val deleted = dao.deleteById(outboxId)
        if (deleted == 0) {
            Log.d(TAG, "markSuccess: id=$outboxId 行已换代/不存在，保留新代 payload")
        }
    }

    /**
     * [T14] CREATE 成功后的 uuid 兑现回填（§3.1「PATCH 的 remoteId 回填为 CREATE 返回的 uuid」）：
     * - 同实体未重放、仍携带旧 clientUuid 的行（PATCH/MEMBER/DELETE）`remoteId` 换成服务端 uuid；
     * - PERSON 创建时，所有 MEMBER 行 payload 里引用旧 clientUuid 的 `personUuid` 同步换新
     *   （成员挂在 Tag/Collection 上，无法按 `(kind, localId)` 定位，按引号包裹的 uuid 精确匹配）。
     *
     * 回填失败不影响创建结果本身——下一轮 pushOnce 的 resolveRemoteId 会按 DB identity 自愈。
     */
    fun backfillAfterCreate(
        entityKind: EntityKind,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (oldRemoteId == newRemoteId) return
        val rows = dao.backfillRemoteId(entityKind.name, localId, oldRemoteId, newRemoteId, now)
        var memberFixed = 0
        if (entityKind == EntityKind.PERSON) {
            // LIKE 匹配必须带通配符，否则 exact match 找不到嵌套在 payload 里的 uuid
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
                    Log.e(TAG, "backfillAfterCreate: MEMBER payload 解析失败 id=${row.id}", e)
                }
            }
        }
        Log.d(
            TAG,
            "backfillAfterCreate: kind=${entityKind.name} localId=$localId rows=$rows memberPayloads=$memberFixed " +
                "old=${oldRemoteId.take(8)} new=${newRemoteId.take(8)}",
        )
    }

    /**
     * 取消同实体未发的 CREATE/PATCH（本地新建未确认上云的实体被删除时用）——
     * 防止「用户已删，CREATE 还在队」把幽灵行推上服务端。
     */
    fun cancelEntity(entityKind: EntityKind, localId: Long): Int {
        val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
        if (cancelled > 0) {
            Log.d(TAG, "cancelEntity: kind=${entityKind.name} localId=$localId cancelled=$cancelled")
        }
        return cancelled
    }

    /**
     * 失败记账：单条 SQL `attempts = attempts + 1` + 指数退避（消灭旧实现的非原子 RMW）。
     * 并发调用不丢计数；落在已换代行上是 no-op。
     */
    fun recordFailure(outboxId: Long, error: Throwable, now: Long = System.currentTimeMillis()) {
        val message = error.message?.take(MAX_LAST_ERROR_LENGTH) ?: error.javaClass.simpleName
        val updated = dao.recordFailure(outboxId, now, BACKOFF_BASE_MILLIS, MAX_BACKOFF_EXPONENT, message)
        if (updated == 0) {
            Log.d(TAG, "recordFailure: id=$outboxId 行已换代/不存在，attempts 不跨代累计")
        } else {
            Log.d(TAG, "recordFailure: id=$outboxId attempts=$updated error=$message")
        }
    }

    /** PATCH 冲突分支：CREATE 忽略（幂等）；PATCH 字段级 merge 后换代重插。 */
    private fun mergeOrIgnore(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        mergeKey: String?,
        payload: JsonObject,
        now: Long,
    ): OutboxEnqueueResult {
        if (op != OutboxOpType.PATCH) {
            Log.d(TAG, "enqueue: CREATE 已在队被忽略 kind=${entityKind.name} localId=$localId")
            return OutboxEnqueueResult.IgnoredDuplicateCreate
        }
        val existing = dao.getByMergeKey(mergeKey!!) ?: run {
            Log.e(TAG, "enqueue: mergeKey=$mergeKey 冲突后行消失，按新行重试处理")
            throw IllegalStateException("outbox merge row vanished: $mergeKey")
        }
        val merged = mergePayload(existing.payloadJson, payload)
        val newRemoteId = remoteId?.takeIf { it.isNotBlank() } ?: existing.remoteId
        dao.deleteById(existing.id)
        val row = newRow(entityKind, localId, newRemoteId, op, mergeKey, merged, now)
            .copy(createdAt = existing.createdAt)
        val newId = tryInsert(row) ?: throw IllegalStateException("outbox re-insert conflicted: $mergeKey")
        Log.d(TAG, "enqueue: merged kind=${entityKind.name} localId=$localId op=PATCH oldId=${existing.id} -> newId=$newId")
        return OutboxEnqueueResult.MergedIntoExisting(newId)
    }

    /** 新 payload 非 null 字段覆盖旧 payload；null/缺省保留旧值（服务端「不更新」语义）。 */
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

    /**
     * 认领：唯一索引放行则返回 rowId，冲突返回 null（语句级 ABORT，外层事务仍有效）。
     * [日志豁免] 冲突返 null 是 INSERT-first 认领的**预期路径**（规格 §3.8 禁止
     * SELECT-then-INSERT），非吞异常——merge 分支内有对应 Log.d。
     */
    private fun tryInsert(entity: OutboxEntity): Long? = try {
        dao.insertOrAbort(entity)
    } catch (e: SQLiteConstraintException) {
        null
    }

    private fun parsePayload(json: String): JsonObject = runCatching {
        BadgerJson.parseToJsonElement(json) as JsonObject
    }.getOrElse { e ->
        Log.e(TAG, "parsePayload: payloadJson 解析失败，按空 payload 处理", e)
        JsonObject(emptyMap())
    }

    companion object {
        private const val TAG = "OutboxStore"

        /** Worker 单批取行上限（一批处理不完由 Worker 循环再取）。 */
        const val DEFAULT_BATCH = 50

        /** 退避基长：第 n 次失败后等待 10s × 2^min(n-1, 6)。 */
        const val BACKOFF_BASE_MILLIS = 10_000L

        /** 退避指数上限，最长单次等待 [MAX_BACKOFF_MILLIS]。 */
        const val MAX_BACKOFF_EXPONENT = 6
        const val MAX_BACKOFF_MILLIS = 640_000L

        /** lastError 截断长度（对齐旧 store）。 */
        const val MAX_LAST_ERROR_LENGTH = 500
    }
}
