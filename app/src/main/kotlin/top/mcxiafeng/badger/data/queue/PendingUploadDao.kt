package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * V2 PendingUpload 队列表 DAO(对应表 `pending_uploads`)。
 *
 * **状态机契约**(对应规约 docs/BADGER_V2_CLIENT_PLAN.md §4.2):
 * ```
 *        enqueue()                       scheduler.kick()
 *  (none) ───────► PENDING ────────────────► IN_FLIGHT ─┬─► DONE            (HTTP 2xx)
 *                                                       ├─► CONFLICT        (HTTP 409,不重试)
 *                                                       ├─► FAILED          (HTTP 5xx/IO,attempts<max)
 *                                                       ├─► FAILED_PERMANENT(attempts>=max)
 *                                                       └─► WITHDRAWN       (用户撤销)
 * ```
 *
 * **消费方**:[sync.PendingUploadWorker] 轮询 [nextReady] → 标 IN_FLIGHT → 调 ServerApi → 落 DONE/CONFLICT/FAILED。
 *
 * **写入顺序契约**(对应规约 §5.5.4 "永不丢消息"):
 * 任何乐观写必须严格按 1. enqueue → 2. write history → 3. write cache → 4. kick scheduler。
 * 如果中途崩溃,Worker 启动时基于服务端 Source of Truth 纠正客户端。
 *
 * **重试契约**:[scheduleRetry] 实现指数退避(§4.4):attempts=1 → 2s,2 → 4s,3 → 8s,...,8+ → 5min。
 */
@Dao
interface PendingUploadDao {

    // ============ 入队 / 查询 ============

    /** 新入队一条 op(主键冲突 = 重复 opId,违反 UUID 唯一性,直接报 ABORT)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(op: PendingUploadEntity)

    /** 批量入队(P11 老数据主动 sync 时使用)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueueAll(ops: List<PendingUploadEntity>)

    /** 按 opId 单条查询(Worker 启动时核对状态)。 */
    @Query("SELECT * FROM pending_uploads WHERE opId = :opId LIMIT 1")
    suspend fun getById(opId: String): PendingUploadEntity?

    @Query("SELECT * FROM pending_uploads WHERE opId = :opId LIMIT 1")
    fun observeById(opId: String): Flow<PendingUploadEntity?>

    /**
     * 拉取下一批可执行 op:
     * - status = 'PENDING' 且 nextAttemptAt <= now(避免还没到退避时间就被并发 Worker 抢跑)
     * - ORDER BY createdAt ASC(保证 FIFO,先入队先发,避免后入队 op 把前面卡住)
     *
     * limit 留 8(规约 §0#4:批量并发上限),WorkManager 串行 batch。
     */
    @Query("""
        SELECT * FROM pending_uploads
        WHERE status = 'PENDING' AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
    """)
    suspend fun nextReady(now: Long, limit: Int = 8): List<PendingUploadEntity>

    /** 历史页 / 测试用:全表快照。 */
    @Query("SELECT * FROM pending_uploads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt DESC")
    suspend fun getAll(): List<PendingUploadEntity>

    /** 队列总条数(测试用)。 */
    @Query("SELECT COUNT(*) FROM pending_uploads")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    // ============ 状态转移(Worker 消费时调用) ============

    /**
     * 标记为 IN_FLIGHT,记录 lastAttemptAt。
     *
     * [修复防御]:必须配合 `WHERE status = 'PENDING'` 防止双 Worker 并发把同一条 op 标两次
     * (返回受影响行数 = 0 即抢锁失败,Worker 应放弃该 op)。
     */
    @Query("""
        UPDATE pending_uploads
        SET status = 'IN_FLIGHT', lastAttemptAt = :lastAttemptAt
        WHERE opId = :opId AND status = 'PENDING'
    """)
    suspend fun markInFlight(opId: String, lastAttemptAt: Long): Int

    /** HTTP 2xx → DONE,Worker 调用。 */
    @Query("UPDATE pending_uploads SET status = 'DONE' WHERE opId = :opId")
    suspend fun markDone(opId: String)

    /**
     * HTTP 409 → CONFLICT,不重试。
     *
     * Worker 在 UI 侧弹"客户端版本 vs 服务端版本"对话框,详见 BADGER_V2_CLIENT_PLAN.md §6.3。
     */
    @Query("UPDATE pending_uploads SET status = 'CONFLICT', lastError = :lastError WHERE opId = :opId")
    suspend fun markConflict(opId: String, lastError: String)

    /**
     * HTTP 5xx / IOException → FAILED,attempts++,下次重试时间 = now + backoff(attempts+1)。
     *
     * [修复防御]:Worker 用 `attempts+1` 而不是 `attempts`(入参已是新值),避免双重 ++。
     * 退避公式:`2_000L << (attempts - 1).coerceIn(0, 8)` 封顶 5min(§4.4)。
     */
    @Query("""
        UPDATE pending_uploads
        SET status = 'FAILED',
            attempts = :attempts,
            lastError = :lastError,
            lastAttemptAt = :now,
            nextAttemptAt = :nextAttemptAt
        WHERE opId = :opId
    """)
    suspend fun markFailed(
        opId: String,
        attempts: Int,
        lastError: String,
        now: Long,
        nextAttemptAt: Long,
    )

    /**
     * attempts >= maxAttempts → FAILED_PERMANENT,停止重试。
     *
     * Worker 在 attempts 达到 max 时调用,UI 状态徽章变红,需用户主动重发。
     */
    @Query("""
        UPDATE pending_uploads
        SET status = 'FAILED_PERMANENT',
            lastError = :lastError,
            lastAttemptAt = :now
        WHERE opId = :opId
    """)
    suspend fun markFailedPermanent(opId: String, lastError: String, now: Long)

    /** 用户撤销(双边同步的"撤销"或纯撤回)→ WITHDRAWN,Worker 见到直接跳过。 */
    @Query("UPDATE pending_uploads SET status = 'WITHDRAWN' WHERE opId = :opId")
    suspend fun markWithdrawn(opId: String)

    /**
     * 关键操作"直发 HTTP + Worker 兜底"的 recover 路径。
     *
     * [修复防御]:P5/P6 阶段 commitDelete/commitMerge 中,直接 HTTP 失败时**不**能简单地把
     * op 标 FAILED — 因为 Worker 还没尝试发,不能让 attempts=0 的 PENDING op 失活。
     * 这里把 op 拉回 PENDING + nextAttemptAt=now,Worker 下次轮询立即接力。
     * lastError 用于 UI 状态徽章显示。
     */
    @Query("""
        UPDATE pending_uploads
        SET status = 'PENDING',
            lastError = :lastError,
            nextAttemptAt = :now
        WHERE opId = :opId AND status = 'IN_FLIGHT'
    """)
    suspend fun recoverFromDirect(opId: String, lastError: String, now: Long): Int

    /** 立即重试(用户从历史页"立即重试"按钮):清错误 + attempts=0 + nextAttemptAt=now。 */
    @Query("""
        UPDATE pending_uploads
        SET status = 'PENDING',
            attempts = 0,
            lastError = NULL,
            nextAttemptAt = :now
        WHERE opId = :opId
    """)
    suspend fun retryNow(opId: String, now: Long)

    // ============ 清理 ============

    /** 删除已 DONE 的 op(避免表膨胀;P9 阶段建议后台周期任务清理)。 */
    @Query("DELETE FROM pending_uploads WHERE status = 'DONE' AND createdAt < :before")
    suspend fun purgeDone(before: Long): Int

    /** 物理删除某条 op(仅 WITHDRAWN/DONE 后才允许,避免误删活跃 op)。 */
    @Query("DELETE FROM pending_uploads WHERE opId = :opId AND status IN ('DONE', 'WITHDRAWN')")
    suspend fun deleteFinished(opId: String): Int
}