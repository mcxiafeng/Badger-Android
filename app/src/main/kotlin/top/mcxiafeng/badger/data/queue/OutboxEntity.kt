package top.mcxiafeng.badger.data.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通用 Outbox 表（规格 §3.1，替代旁路表 `pending_person_updates`）。
 *
 * Person / Tag / Collection 的远端写意图（PATCH / DELETE / MEMBER_* / Phase 3 的 CREATE）
 * 统一落此表，由 OutboxWorker 按重试节奏重放。行在成功前不删（保留期盖过最长重试链）。
 *
 * 关键设计：
 * - `entityKind` + `localId` 定位本地行；`remoteId` 是服务端 uuid（Phase 3 的 CREATE 行
 *   改存 clientUuid，创建成功后由 CreateOnPush 回填）。
 * - `mergeKey` 实现规格里 `UNIQUE(entityKind, localId, op) WHERE op IN ('CREATE','PATCH')`
 *   的**部分唯一索引**：Room 注解无法表达 WHERE 子句，改为 CREATE/PATCH 行写入
 *   `"$kind:$localId:$op"`、MEMBER 与 DELETE 行写 NULL —— SQLite 唯一索引对 NULL 不去重，
 *   MEMBER_* 因此天然保持 FIFO 多行。认领（首插 or 并入已有行）靠该索引原子完成。
 * - `attempts` / `nextAttemptAt` / `lastError` 构成退避状态机：recordFailure 必须单条 SQL
 *   `attempts = attempts + 1`（消灭 C17 非原子 RMW）。
 */
@Entity(
    tableName = "outbox",
    indices = [
        // 规格中的 index_outbox_ready：Worker 取「到期待重放」行的扫描索引
        Index(value = ["nextAttemptAt", "entityKind"]),
        // CREATE/PATCH 的认领索引（见 mergeKey KDoc）
        Index(value = ["mergeKey"], unique = true),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityKind: String,
    val localId: Long,
    val remoteId: String?,
    val op: String,
    val mergeKey: String?,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long,
    val lastError: String? = null,
)
