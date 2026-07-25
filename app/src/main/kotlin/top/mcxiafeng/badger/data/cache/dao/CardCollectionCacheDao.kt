package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.CardCollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity

/**
 * V2 名片夹 DAO(对应表 `card_collections_cache`)。
 *
 * [A3] 新增 `getCollectionsWithCount()`:走 V1 `scan_results` 表统计联系人数量。
 */
@Dao
interface CardCollectionCacheDao {

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CardCollectionCacheEntity>>

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity>

    @Query("SELECT * FROM card_collections_cache WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): CardCollectionCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CardCollectionCacheEntity): Long

    @Update
    suspend fun updateCollection(collection: CardCollectionCacheEntity)

    /**
     * [A3] 名片夹 + 联系人数量。
     *
     * V2 cache 阶段走 V1 `scan_results` 表统计联系人去重数;后续 P11 阶段考虑迁到 cache 表。
     */
    @Query("""
        SELECT cc.*, COUNT(DISTINCT sr.contactId) AS contactCount
        FROM card_collections_cache cc
        LEFT JOIN scan_results sr ON cc.id = sr.collectionId
        GROUP BY cc.id
        ORDER BY cc.name ASC
    """)
    fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>>
}