package top.mcxiafeng.badger.data.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox",
    indices = [
        
        Index(value = ["nextAttemptAt", "entityKind"]),
        
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
