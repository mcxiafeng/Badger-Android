package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity

/**
 * [Phase 2] `person_profile_cache` DAO。
 *
 * 一对一子表：按 `contactServerId`（服务端 Person uuid）查询/写入。
 */
@Dao
interface PersonProfileCacheDao {

    @Query("SELECT * FROM person_profile_cache WHERE contactServerId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): PersonProfileCacheEntity?

    /** upsert：存在则更新，不存在则插入（按 contactServerId 唯一索引）。 */
    @Upsert
    suspend fun upsert(entity: PersonProfileCacheEntity)

    @Query("DELETE FROM person_profile_cache WHERE contactServerId = :serverId")
    suspend fun deleteByServerId(serverId: String)
}
