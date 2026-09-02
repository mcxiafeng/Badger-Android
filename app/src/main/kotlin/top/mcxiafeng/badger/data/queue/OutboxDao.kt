package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Outbox DAO。方法刻意**不 suspend**：OutboxStore 的 enqueue/merge 是多语句阻塞事务
 * （与旧 PendingPersonUpdateStore 同模式），调用方（Repository / Worker）已在 IO 线程。
 */
@Dao
interface OutboxDao {

    /** Worker 取「到期待重放」行：FIFO（createdAt → id 稳定序）。 */
    @Query(
        "SELECT * FROM outbox WHERE nextAttemptAt <= :now " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    fun getReady(now: Long, limit: Int): List<OutboxEntity>

    /** 按 CREATE/PATCH 认领索引取已有行（仅 merge 分支在约束冲突后调用）。 */
    @Query("SELECT * FROM outbox WHERE mergeKey = :mergeKey LIMIT 1")
    fun getByMergeKey(mergeKey: String): OutboxEntity?

    /**
     * 认领新行：唯一索引冲突时抛 [android.database.sqlite.SQLiteConstraintException]，
     * 由 OutboxStore 捕获后走 merge 分支。语句级 ABORT 只回滚本语句，外层事务继续有效。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertOrAbort(entity: OutboxEntity): Long

    /** 成功出队（行在成功前不删，此处是唯一删除时机之一）。 */
    @Query("DELETE FROM outbox WHERE id = :id")
    fun deleteById(id: Long): Int

    /** DELETE 入队时取消同实体未发的 CREATE/PATCH（没必要推一个马上要删的对象）。 */
    @Query(
        "DELETE FROM outbox WHERE entityKind = :entityKind AND localId = :localId " +
            "AND op IN ('CREATE', 'PATCH')"
    )
    fun deleteUnsentCreateAndPatch(entityKind: String, localId: Long): Int

    /**
     * 原子失败记账：`attempts = attempts + 1` 单条 SQL（消灭 C17 读-改-写竞态），
     * 退避时长在 SQL 内用**旧 attempts** 计算（SQLite SET 表达式看到旧值），
     * 与 Kotlin 侧 `10s << min(attempts-1, 6)` 完全同式。
     */
    @Query(
        "UPDATE outbox SET " +
            "attempts = attempts + 1, " +
            "nextAttemptAt = :now + :baseBackoffMillis * (1 << min(attempts, :maxBackoffExponent)), " +
            "updatedAt = :now, " +
            "lastError = :lastError " +
            "WHERE id = :id"
    )
    fun recordFailure(
        id: Long,
        now: Long,
        baseBackoffMillis: Long,
        maxBackoffExponent: Int,
        lastError: String?,
    ): Int

    /** 诊断/日志用计数。 */
    @Query("SELECT COUNT(*) FROM outbox")
    fun count(): Int
}
