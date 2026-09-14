package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val id: Long = 1L,
    val lastVersion: Long = 0L,
    val updatedAt: Long = 0L,
)
