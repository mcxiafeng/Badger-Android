package top.mcxiafeng.badger.data.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 操作历史表（用户的"反悔入口"）。
 *
 * 记录所有操作（含未同步的）的 before/after 快照 + 反向 op JSON，
 * 支持撤销（双边同步：服务端反向 PATCH + 本地回滚）+ 重发 + 解决冲突。
 *
 * 关键设计：
 * - `snapshotBeforeJson` 含修改前完整对象 JSON（撤销时回滚本地缓存）
 * - `inversePayloadJson` 含反向 PATCH JSON（撤销时入 PendingUpload 队列）
 * - `canUndo` / `canReplay` 用于 UI 禁用按钮
 * - `opStatus` 状态机与 PendingUploadEntity.status 对齐
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 / §6
 */
@Entity(
    tableName = "operation_history",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["opStatus"]),
        Index(value = ["contactId"]),
    ]
)
data class OperationHistoryEntity(
    @PrimaryKey val opId: String,
    val contactId: Long,
    val opType: String,
    val opLabel: String,
    val payloadJson: String,
    val snapshotBeforeJson: String,
    val snapshotAfterJson: String? = null,
    val createdAt: Long,
    val opStatus: String,
    val serverVersion: Long? = null,
    val lastError: String? = null,
    val attempts: Int = 0,
    val inversePayloadJson: String? = null,
    val canUndo: Boolean,
    val canReplay: Boolean,
)
