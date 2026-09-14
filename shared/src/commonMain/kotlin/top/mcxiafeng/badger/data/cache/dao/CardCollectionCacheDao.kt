package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.model.CardCollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity

@Dao
interface CardCollectionCacheDao {

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CardCollectionCacheEntity>>

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity>

    @Query("SELECT * FROM card_collections_cache WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): CardCollectionCacheEntity?

    
    @Query("SELECT * FROM card_collections_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getCollectionByServerId(serverId: String): CardCollectionCacheEntity?

    
    @Query("DELETE FROM card_collections_cache WHERE serverId = :serverId")
    suspend fun deleteCollectionByServerId(serverId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CardCollectionCacheEntity): Long

    @Update
    suspend fun updateCollection(collection: CardCollectionCacheEntity)

    

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT cc.*, COUNT(DISTINCT cm.contactId) AS contactCount
        FROM card_collections_cache cc
        LEFT JOIN collection_member_cache cm ON cc.id = cm.collectionId
        GROUP BY cc.id
        ORDER BY cc.name ASC
    """)
    fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>>

    
    @Query("SELECT COUNT(*) FROM card_collections_cache")
    fun observeRowCount(): Flow<Int>

    

    @Query("SELECT * FROM card_collections_cache WHERE isLocalOnly = 1 OR serverId IS NULL ORDER BY id ASC")
    suspend fun getNeverSyncedCollectionsOnce(): List<CardCollectionCacheEntity>

    
    @Query("DELETE FROM card_collections_cache")
    suspend fun clearAll()
}