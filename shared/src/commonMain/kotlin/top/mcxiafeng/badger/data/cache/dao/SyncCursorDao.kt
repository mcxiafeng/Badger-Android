package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity

@Dao
interface SyncCursorDao {

    @Query("SELECT lastVersion FROM sync_cursor WHERE id = 1")
    suspend fun getLastVersion(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    
    @Query("DELETE FROM sync_cursor WHERE id = 1")
    suspend fun clearAll()
}
