package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity

/**
 * [Phase 3] sync 游标 DAO（`sync_cursor` 单例行）。
 *
 * 读：`getLastVersion()` 返回 null 表示从未同步（since=0 全量重放）。
 * 写：`upsert` 在每批 `SyncChange` 成功重放落库后推进 [SyncCursorEntity.lastVersion]。
 */
@Dao
interface SyncCursorDao {

    @Query("SELECT lastVersion FROM sync_cursor WHERE id = 1")
    suspend fun getLastVersion(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)
}
