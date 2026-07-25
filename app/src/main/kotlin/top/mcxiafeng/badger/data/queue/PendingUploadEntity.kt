package top.mcxiafeng.badger.data.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 PendingUpload 队列表（杀后台不丢）。
 *
 * 单一事实源：所有乐观写的状态都在这张表；Worker 只是消费器。
 * P4 阶段由 `PendingUploadWorker` 消费；P6 阶段加上 `priority` 列支持关键操作双通道。
 *
 * 关键设计：
 * - `opId` 是 UUID，作为主键与 OperationHistoryEntity 关联
 * - `status` 状态机：PENDING → IN_FLIGHT → (DONE / FAILED / CONFLICT / WITHDRAWN)
 * - `nextAttemptAt` + `attempts` 实现指数退避（§4.4）
 * - `deviceId` 用于多设备冲突排查
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 / §4
 */
@Entity(
    tableName = "pending_uploads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["contactId"]),
        Index(value = ["nextAttemptAt"]),
    ]
)
data class PendingUploadEntity(
    @PrimaryKey val opId: String,
    val contactId: Long,
    val opType: String,
    val resourceVersion: Long,
    val payloadJson: String,
    val createdAt: Long,
    val status: String,
    val attempts: Int = 0,
    val maxAttempts: Int = 8,
    val lastError: String? = null,
    val nextAttemptAt: Long = createdAt,
    val lastAttemptAt: Long? = null,
    val deviceId: String,
)
