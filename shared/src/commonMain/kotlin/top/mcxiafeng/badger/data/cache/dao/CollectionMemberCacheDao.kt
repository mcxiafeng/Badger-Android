package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity

/**
 * V2 名片夹成员关联 DAO（对应表 `collection_member_cache`）。
 *
 * 替代 V1 `ScanResultDao` 作为联系人 ↔ 名片夹 多对多关联的读写路径。
 * 仅保留关联关系，不再存储扫码元数据。
 *
 * 对应规约：docs/architecture-refactor-plan.md Phase 4 Task #20
 */
@Dao
interface CollectionMemberCacheDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(member: CollectionMemberCacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(members: List<CollectionMemberCacheEntity>)

    @Query("DELETE FROM collection_member_cache WHERE contactId = :contactId AND collectionId = :collectionId")
    suspend fun delete(contactId: Long, collectionId: Long)

    @Query("DELETE FROM collection_member_cache WHERE contactId IN (:contactIds) AND collectionId = :collectionId")
    suspend fun deleteByContactsAndCollection(contactIds: List<Long>, collectionId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM collection_member_cache WHERE contactId = :contactId AND collectionId = :collectionId)")
    suspend fun exists(contactId: Long, collectionId: Long): Boolean

    @Query("SELECT DISTINCT collectionId FROM collection_member_cache WHERE contactId = :contactId")
    fun observeCollectionIdsByContact(contactId: Long): Flow<List<Long>>

    @Query("SELECT DISTINCT collectionId FROM collection_member_cache WHERE contactId = :contactId")
    suspend fun getCollectionIdsByContact(contactId: Long): List<Long>

    @Query("SELECT contactId, COUNT(*) AS memberCount FROM collection_member_cache WHERE collectionId = :collectionId GROUP BY contactId")
    suspend fun getMemberCountsByCollection(collectionId: Long): Map<@androidx.room.MapColumn(columnName = "contactId") Long, @androidx.room.MapColumn(columnName = "memberCount") Int>

    @Query("SELECT COUNT(*) FROM collection_member_cache WHERE collectionId = :collectionId")
    suspend fun countByCollection(collectionId: Long): Int
}
