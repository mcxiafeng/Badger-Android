package top.mcxiafeng.badger.data.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
