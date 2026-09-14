package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity

@Dao
interface PersonProfileCacheDao {

    @Query("SELECT * FROM person_profile_cache WHERE contactServerId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): PersonProfileCacheEntity?

    
    @Upsert
    suspend fun upsert(entity: PersonProfileCacheEntity)

    @Query("DELETE FROM person_profile_cache WHERE contactServerId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    
    @Query("DELETE FROM person_profile_cache")
    suspend fun clearAll()
}
