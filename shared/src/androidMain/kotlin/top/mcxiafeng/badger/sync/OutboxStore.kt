package top.mcxiafeng.badger.sync

import androidx.room.withTransaction
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.data.queue.OutboxDao
import top.mcxiafeng.badger.data.queue.OutboxEntity
import top.mcxiafeng.badger.shared.util.nowMs

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
 * - 认领靠 `outbox.mergeKey` 唯一索引（`kind:localId:op`，CREATE/PATCH 行非 NULL）。
 *   [K13 修复] 原 INSERT-first-then-merge 模式依赖「事务内吞语句级 ABORT 异常」，
 *   KMP driver 下该模式破坏事务（见 enqueue 内注释），改 SELECT 探测先行；
 *   唯一索引仍保留作并发防线（真冲突时 fail-fast 由上层重试）。
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
 * 线程模型：[KMP K13b] 阻塞式多语句事务改走 `RoomDatabase.withTransaction`（KMP 协程事务，
 * bundled driver 语义等价）；DAO 全量 suspend（非 Android target 只允许 suspend DAO）。
 * 并发 enqueue/recordFailure 的原子性由 SQLite 事务 + 单条 UPDATE 保证。
 */
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
        return database.withTransaction {
            if (op == OutboxOpType.DELETE) {
                val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
                if (cancelled > 0) {
                    BadgerLog.d(TAG, "enqueue DELETE: cancelled $cancelled unsent CREATE/PATCH kind=${entityKind.name} localId=$localId")
                }
            }
            // [K13 修复] INSERT-first + 事务内吞约束异常的模式在 KMP bundled/framework driver 下
            // 不可用：语句级冲突异常会把整个事务标记为死事务，后续 delete+reinsert 在提交时
            // 全部回滚（诊断：OutboxDiagTest，merge 返回 MergedIntoExisting 但 DB 仅剩旧行）。
            // 改为 SELECT 探测 → 无行则 INSERT / 有行则 merge。enqueue 由单进程调用链串行
            // 触达（repository Mutex），真并发冲突时 INSERT 直接抛（fail-fast），上层重试语义安全。
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

    /**
     * 取「到期待重放」行，FIFO（createdAt → id 稳定序）。
     *
     * [includeBackoff]：手动「立即同步」（T17 syncOnce）传 true——无视退避窗口立即重试全部行；
     * WorkManager 触发的自动重放保持 false，尊重 recordFailure 的指数退避。
     */
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

    /**
     * 成功出队。行 id 若已被后续 merge 换代（返回 0 行），说明旧代回执过期，
     * 新代 payload 留在队内等待重放——这是防丢编辑的关键路径，不是异常。
     */
    override suspend fun markSuccess(outboxId: Long) {
        val deleted = dao.deleteById(outboxId)
        if (deleted == 0) {
            BadgerLog.d(TAG, "markSuccess: id=$outboxId 行已换代/不存在，保留新代 payload")
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

    /**
     * 取消同实体未发的 CREATE/PATCH（本地新建未确认上云的实体被删除时用）——
     * 防止「用户已删，CREATE 还在队」把幽灵行推上服务端。
     */
    override suspend fun cancelEntity(entityKind: EntityKind, localId: Long): Int {
        val cancelled = dao.deleteUnsentCreateAndPatch(entityKind.name, localId)
        if (cancelled > 0) {
            BadgerLog.d(TAG, "cancelEntity: kind=${entityKind.name} localId=$localId cancelled=$cancelled")
        }
        return cancelled
    }

    /**
     * 失败记账：单条 SQL `attempts = attempts + 1` + 指数退避（消灭旧实现的非原子 RMW）。
     * 并发调用不丢计数；落在已换代行上是 no-op。
     */
    override suspend fun recordFailure(outboxId: Long, error: Throwable, now: Long) {
        val message = error.message?.take(MAX_LAST_ERROR_LENGTH) ?: error::class.simpleName ?: "Exception"
        val updated = dao.recordFailure(outboxId, now, BACKOFF_BASE_MILLIS, MAX_BACKOFF_EXPONENT, message)
        if (updated == 0) {
            BadgerLog.d(TAG, "recordFailure: id=$outboxId 行已换代/不存在，attempts 不跨代累计")
        } else {
            BadgerLog.d(TAG, "recordFailure: id=$outboxId attempts=$updated error=$message")
        }
    }

    /** PATCH 冲突分支：CREATE 忽略（幂等）；PATCH 字段级 merge 后换代重插。 */
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

    private fun parsePayload(json: String): JsonObject = runCatching {
        BadgerJson.parseToJsonElement(json) as JsonObject
    }.getOrElse { e ->
        BadgerLog.e(TAG, "parsePayload: payloadJson 解析失败，按空 payload 处理", e)
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
