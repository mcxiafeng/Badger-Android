package top.mcxiafeng.badger.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Opaque transactional sync metadata, owned by the same database as the business rows. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM sync_state ORDER BY `key`")
    suspend fun getAll(): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
