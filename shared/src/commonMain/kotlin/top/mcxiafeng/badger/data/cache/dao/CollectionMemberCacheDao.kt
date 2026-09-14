package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity

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

    
    @Query("DELETE FROM collection_member_cache")
    suspend fun clearAll()
}
