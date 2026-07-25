package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * V2 操作历史表 DAO(对应表 `operation_history`)。
 *
 * **核心契约**(对应规约 docs/BADGER_V2_CLIENT_PLAN.md §6):
 * - 每条 op 在被 `enqueue` 时**同步**写入 history(含 snapshotBefore + inversePayloadJson),
 *   作为用户的"反悔入口"。"撤销"操作 = 用 inversePayloadJson 反向 PATCH 服务端 +
 *   用 snapshotBeforeJson 回滚本地缓存。
 * - `opStatus` 与 `pending_uploads.status` 对齐(同一 opId),便于 JOIN 读最新状态。
 * - `canUndo` / `canReplay` 由调用方在插入时根据 opType 决定:
 *   - 创建联系人 → canUndo=true(撤销=删除)
 *   - 删除联系人 → canUndo=false,canReplay=true(恢复需要新 op)
 *   - 改备注 → canUndo=true,canReplay=false(改回=撤销的反向 op)
 *
 * **消费方**:[pages.settings.OperationHistoryPage] (P7)展示 + 调用
 * [undo] / [replay] / [resolveLocal] / [resolveServer]。
 */
@Dao
interface OperationHistoryDao {

    // ============ 写入 / 查询 ============

    /** 写入一条 history(必须与 PendingUploadDao.enqueue 成对调用,opId 相同)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(op: OperationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(ops: List<OperationHistoryEntity>)

    /** 按 opId 查询(撤销/重发时读取 inversePayloadJson + snapshotBeforeJson)。 */
    @Query("SELECT * FROM operation_history WHERE opId = :opId LIMIT 1")
    suspend fun getById(opId: String): OperationHistoryEntity?

    @Query("SELECT * FROM operation_history WHERE opId = :opId LIMIT 1")
    fun observeById(opId: String): Flow<OperationHistoryEntity?>

    /**
     * 历史页列表(按时间倒序)。
     *
     * [修复防御]:分页用 offset+limit 而非 Flow Paging,V2 不依赖 Paging3(§14.7.1 已删)。
     * offset 不带 index,因为 history 主要按 createdAt DESC,翻页慢一点无所谓。
     */
    @Query("""
        SELECT * FROM operation_history
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPage(limit: Int, offset: Int): List<OperationHistoryEntity>

    /** 历史页首屏 Flow(直接订阅,无需调用方主动 reload)。 */
    @Query("SELECT * FROM operation_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<OperationHistoryEntity>>

    /** 按联系人筛选(联系人详情页"该联系人的历史操作"侧栏用)。 */
    @Query("""
        SELECT * FROM operation_history
        WHERE contactId = :contactId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getByContact(contactId: Long, limit: Int = 50): List<OperationHistoryEntity>

    @Query("""
        SELECT * FROM operation_history
        WHERE contactId = :contactId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeByContact(contactId: Long, limit: Int = 50): Flow<List<OperationHistoryEntity>>

    /** 状态徽章筛选:仅显示 CONFLICT / FAILED(待用户处理)。 */
    @Query("""
        SELECT * FROM operation_history
        WHERE opStatus IN ('CONFLICT', 'FAILED_PERMANENT')
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observePending(limit: Int = 50): Flow<List<OperationHistoryEntity>>

    /** 统计(测试用 / UI 顶栏徽章数字)。 */
    @Query("SELECT COUNT(*) FROM operation_history")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM operation_history WHERE opStatus = :status")
    suspend fun countByStatus(status: String): Int

    // ============ 状态转移(与 PendingUploadDao 同步状态) ============

    /**
     * Worker 成功 → DONE + 写回 serverVersion(供下一次 PATCH 的 If-Match 用)。
     *
     * [修复防御]:serverVersion 是 opStatus 之外的独立列,不能用 lastError 列转义,
     * 这里单独 UPDATE 两列。
     */
    @Query("""
        UPDATE operation_history
        SET opStatus = 'DONE',
            serverVersion = :serverVersion,
            snapshotAfterJson = :snapshotAfterJson,
            lastError = NULL
        WHERE opId = :opId
    """)
    suspend fun markDone(opId: String, serverVersion: Long?, snapshotAfterJson: String?)

    /**
     * 409 CONFLICT:写回服务端 currentVersion + snapshot(规约 S3 响应体)。
     *
     * UI 弹"客户端 vs 服务端"对话框,详见 BADGER_V2_CLIENT_PLAN.md §6.3。
     */
    @Query("""
        UPDATE operation_history
        SET opStatus = 'CONFLICT',
            serverVersion = :serverVersion,
            lastError = :lastError
        WHERE opId = :opId
    """)
    suspend fun markConflict(opId: String, serverVersion: Long?, lastError: String)

    /** 5xx / IO → FAILED:attempts++ + 错误信息(不重置 attempts,让 UI 显示累计)。 */
    @Query("""
        UPDATE operation_history
        SET opStatus = 'FAILED',
            attempts = :attempts,
            lastError = :lastError
        WHERE opId = :opId
    """)
    suspend fun markFailed(opId: String, attempts: Int, lastError: String)

    /** 用户撤销 → WITHDRAWN(不影响 canUndo 标记,撤回可再次撤回)。 */
    @Query("UPDATE operation_history SET opStatus = 'WITHDRAWN', lastError = NULL WHERE opId = :opId")
    suspend fun markWithdrawn(opId: String)

    /** 立即重试:不修改 opStatus,UI 可选地清 lastError 让用户看到"已重发"。 */
    @Query("UPDATE operation_history SET lastError = NULL, attempts = attempts + 1 WHERE opId = :opId")
    suspend fun touchRetry(opId: String)

    // ============ 清理 ============

    /**
     * 删除 N 天前的 history(防止表膨胀)。
     *
     * [修复防御]:保留"待用户处理"的活跃状态:
     * - CONFLICT / FAILED:用户可能想"采用本地"或"采用服务端",自动清理 = 偷走用户决策权
     * - PENDING / IN_FLIGHT:队列中,Worker 还没消化完
     * - WITHDRAWN:用户的"反悔入口",撤销某 op 后还能再撤销
     *
     * 仅 DONE / FAILED_PERMANENT 进入终态后清理。
     */
    @Query("""
        DELETE FROM operation_history
        WHERE createdAt < :before
          AND opStatus IN ('DONE', 'FAILED_PERMANENT')
    """)
    suspend fun purgeOld(before: Long): Int
}