package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [Phase 3] 多端增量同步游标（`GET /api/user/sync?since=` 消费进度）。
 *
 * 单例记录（id = 1L）。[lastVersion] 是已成功重放进 Room 的最后一条
 * `SyncChange.version`（服务端 `UserHistory.version`，owner 域内严格单调递增）。
 * 拉取时以它作为下一轮 `since`；首次同步（无行）视为 0 = 全量重放。
 */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val id: Long = 1L,
    val lastVersion: Long = 0L,
    val updatedAt: Long = 0L,
)
