package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactPlatformCacheEntity

/**
 * V2 联系人平台条目 DAO（对应表 `contact_platforms_cache`）。
 */
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

    /**
     * [V2-P6] 关键操作 commitDelete 双通道:服务端 200 后(或 30s revert 已恢复后)清掉关联子表。
     * 直接按 contactId 全删,避免 DELETE_CONTACT 走完还残留平台数据导致 UI 列表"半截"。
     */
    @Query("DELETE FROM contact_platforms_cache WHERE contactId = :contactId")
    suspend fun deleteByContact(contactId: Long)

    @Query("SELECT * FROM contact_platforms_cache WHERE contactId IN (:contactIds)")
    suspend fun getPlatformsByContacts(contactIds: List<Long>): List<ContactPlatformCacheEntity>

    @Query("SELECT * FROM contact_platforms_cache")
    suspend fun getAllPlatforms(): List<ContactPlatformCacheEntity>

    /**
     * 批量查重（QAuxv 导入）：返回 platformKey 指定的所有匹配 value 的平台条目。
     */
    @Query("SELECT * FROM contact_platforms_cache WHERE platformKey = :platformKey AND value IN (:values)")
    suspend fun getPlatformsByKeyAndValues(platformKey: String, values: List<String>): List<ContactPlatformCacheEntity>

    /**
     * [V2-P1 A2] 按 platformKey + value 找出对应的 contactId 集合(用于 checkDuplicate)。
     *
     * V2 cache 阶段退化为只返回 contactId 集合，
     * 由 Repository 层二次拉 ContactCacheEntity → Contact。SQLite 的 IN 操作做 contains。
     */
    @Query("""
        SELECT DISTINCT contactId FROM contact_platforms_cache
        WHERE platformKey = :platformKey AND value = :value AND contactId != :excludeId
        LIMIT 5
    """)
    suspend fun findContactIdsByPlatform(platformKey: String, value: String, excludeId: Long): List<Long>
}
