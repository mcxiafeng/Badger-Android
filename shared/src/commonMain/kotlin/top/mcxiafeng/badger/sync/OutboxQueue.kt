package top.mcxiafeng.badger.sync

/**
 * [KMP K08-B] Outbox 写意图队列的 common 契约（业务层迁 commonMain 的类型边界）。
 *
 * 实现类 `OutboxStore`（shared androidMain，显式事务走 openHelper）；
 * repository/SyncEngine 依赖本接口。方法语义见 OutboxStore 注释。
 */
interface OutboxQueue {
    /** 写意图入队（INSERT-first + mergeKey 认领 + 字段级 merge）。返回认领结果。 */
    suspend fun enqueue(
        entityKind: EntityKind,
        localId: Long,
        remoteId: String?,
        op: OutboxOpType,
        payload: kotlinx.serialization.json.JsonObject,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    ): OutboxEnqueueResult

    /** 取消同实体未发的 CREATE/PATCH（DELETE 入队前调用），返回取消行数。 */
    suspend fun cancelEntity(entityKind: EntityKind, localId: Long): Int

    /** [K13b 扩约/suspend 化] 取「到期待重放」行，FIFO；includeBackoff=true 无视退避窗口（手动同步用）。 */
    suspend fun getReady(
        limit: Int = 20,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
        includeBackoff: Boolean = false,
    ): List<OutboxOp>

    /** [K13b 扩约] 成功出队（行已换代时为 no-op，新代 payload 留队重放）。 */
    suspend fun markSuccess(outboxId: Long)

    /** [K13b 扩约] 失败记账：attempts 自增 + 指数退避（单条 SQL，原子）。 */
    suspend fun recordFailure(outboxId: Long, error: Throwable, now: Long = top.mcxiafeng.badger.shared.util.nowMs())

    /** [K13b 扩约] CREATE 成功后的 uuid 兑现回填（PATCH/MEMBER/DELETE 行 remoteId 换代）。 */
    suspend fun backfillAfterCreate(
        entityKind: EntityKind,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long = top.mcxiafeng.badger.shared.util.nowMs(),
    )
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
