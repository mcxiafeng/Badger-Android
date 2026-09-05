package top.mcxiafeng.badger.data.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Outbox DAO。[KMP K13b] 全量 suspend 化（Room KMP：非 Android target 的 DAO 只允许
 * suspend 方法；OutboxStore 事务改走 withTransaction 协程事务）。
 */
@Dao
interface OutboxDao {

    /** Worker 取「到期待重放」行：FIFO（createdAt → id 稳定序）。 */
    @Query(
        "SELECT * FROM outbox WHERE nextAttemptAt <= :now " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    suspend fun getReady(now: Long, limit: Int): List<OutboxEntity>

    /** [T17] 手动「立即同步」用：无视退避窗口取全部行（用户触发 = 立即重试）。 */
    @Query(
        "SELECT * FROM outbox " +
            "ORDER BY createdAt ASC, id ASC LIMIT :limit"
    )
    suspend fun getReadyIncludingBackoff(limit: Int): List<OutboxEntity>

    /** 按 CREATE/PATCH 认领索引取已有行（仅 merge 分支在约束冲突后调用）。 */
    @Query("SELECT * FROM outbox WHERE mergeKey = :mergeKey LIMIT 1")
    suspend fun getByMergeKey(mergeKey: String): OutboxEntity?

    /**
     * 认领新行：唯一索引冲突时抛 [android.database.sqlite.SQLiteConstraintException]，
     * 由 OutboxStore 捕获后走 merge 分支。语句级 ABORT 只回滚本语句，外层事务继续有效。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrAbort(entity: OutboxEntity): Long

    /** 成功出队（行在成功前不删，此处是唯一删除时机之一）。 */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** DELETE 入队时取消同实体未发的 CREATE/PATCH（没必要推一个马上要删的对象）。 */
    @Query(
        "DELETE FROM outbox WHERE entityKind = :entityKind AND localId = :localId " +
            "AND op IN ('CREATE', 'PATCH')"
    )
    suspend fun deleteUnsentCreateAndPatch(entityKind: String, localId: Long): Int

    /**
     * [T14] CREATE 成功后的 remoteId 回填：同实体尚未重放、仍携带旧 clientUuid 的行
     * （PATCH/MEMBER/DELETE）换成服务端 uuid。
     */
    @Query(
        "UPDATE outbox SET remoteId = :newRemoteId, updatedAt = :now " +
            "WHERE entityKind = :entityKind AND localId = :localId AND remoteId = :oldRemoteId"
    )
    suspend fun backfillRemoteId(
        entityKind: String,
        localId: Long,
        oldRemoteId: String,
        newRemoteId: String,
        now: Long,
    ): Int

    /**
     * [T14] 找出 payload 中引用某 person uuid 的 MEMBER 行（引号包裹防误伤）。
     * Person CREATE 兑现新 uuid 后，这些行的 `personUuid` 需要同步换新，否则成员子接口 404 死循环。
     */
    @Query(
        "SELECT * FROM outbox WHERE op IN ('MEMBER_ADD', 'MEMBER_REMOVE') " +
            "AND payloadJson LIKE :quotedUuidNeedle"
    )
    suspend fun getMemberRowsReferencing(quotedUuidNeedle: String): List<OutboxEntity>

    /** [T14] MEMBER 行 payload 的 personUuid 回填写回。 */
    @Query("UPDATE outbox SET payloadJson = :payloadJson, updatedAt = :now WHERE id = :id")
    suspend fun updatePayloadJson(id: Long, payloadJson: String, now: Long): Int

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
    suspend fun recordFailure(
        id: Long,
        now: Long,
        baseBackoffMillis: Long,
        maxBackoffExponent: Int,
        lastError: String?,
    ): Int

    /** 诊断/日志用计数。 */
    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int
}
