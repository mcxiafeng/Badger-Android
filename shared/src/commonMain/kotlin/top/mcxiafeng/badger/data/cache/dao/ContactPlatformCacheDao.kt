package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity

@Dao
interface ContactPlatformCacheDao {

    @Query("SELECT * FROM contact_platforms_cache WHERE contactId = :contactId")
    suspend fun getPlatformsByContact(contactId: Long): List<ContactPlatformCacheEntity>

    @Query("SELECT * FROM contact_platforms_cache WHERE contactId = :contactId")
    fun observePlatformsByContact(contactId: Long): Flow<List<ContactPlatformCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatform(platform: ContactPlatformCacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatforms(platforms: List<ContactPlatformCacheEntity>)

    @Query("DELETE FROM contact_platforms_cache WHERE contactId = :contactId AND platformKey = :platformKey")
    suspend fun deleteByContactAndKey(contactId: Long, platformKey: String)

    

    @Query("DELETE FROM contact_platforms_cache WHERE contactId = :contactId")
    suspend fun deleteByContact(contactId: Long)

    @Query("SELECT * FROM contact_platforms_cache WHERE contactId IN (:contactIds)")
    suspend fun getPlatformsByContacts(contactIds: List<Long>): List<ContactPlatformCacheEntity>

    @Query("SELECT * FROM contact_platforms_cache")
    suspend fun getAllPlatforms(): List<ContactPlatformCacheEntity>

    

    @Query("SELECT * FROM contact_platforms_cache WHERE platformKey = :platformKey AND value IN (:values)")
    suspend fun getPlatformsByKeyAndValues(platformKey: String, values: List<String>): List<ContactPlatformCacheEntity>

    

    @Query("""
        SELECT DISTINCT contactId FROM contact_platforms_cache
        WHERE platformKey = :platformKey AND value = :value AND contactId != :excludeId
        LIMIT 5
    """)
    suspend fun findContactIdsByPlatform(platformKey: String, value: String, excludeId: Long): List<Long>

    
    @Query("DELETE FROM contact_platforms_cache")
    suspend fun clearAll()
}
