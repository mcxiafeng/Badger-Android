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

/**
 * V2 名片夹 DAO(对应表 `card_collections_cache`)。
 *
 * [Phase 4 Task #20] `getCollectionsWithCount` 从 `scan_results` 迁移到 `collection_member_cache`。
 */
@Dao
interface CardCollectionCacheDao {

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CardCollectionCacheEntity>>

    @Query("SELECT * FROM card_collections_cache ORDER BY name ASC")
    suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity>

    @Query("SELECT * FROM card_collections_cache WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): CardCollectionCacheEntity?

    /** [Phase 3] 按服务端 uuid 查本地行（sync 重放定位）。 */
    @Query("SELECT * FROM card_collections_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getCollectionByServerId(serverId: String): CardCollectionCacheEntity?

    /** [Phase 3] 删除本地行（sync REMOVE 重放）。 */
    @Query("DELETE FROM card_collections_cache WHERE serverId = :serverId")
    suspend fun deleteCollectionByServerId(serverId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CardCollectionCacheEntity): Long

    @Update
    suspend fun updateCollection(collection: CardCollectionCacheEntity)

    /**
     * 名片夹 + 联系人数量。
     *
     * [Phase 4 Task #20] 从 `scan_results` 迁移到 `collection_member_cache`。
     */
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT cc.*, COUNT(DISTINCT cm.contactId) AS contactCount
        FROM card_collections_cache cc
        LEFT JOIN collection_member_cache cm ON cc.id = cm.collectionId
        GROUP BY cc.id
        ORDER BY cc.name ASC
    """)
    fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>>

    /** [C1] Dashboard 名片夹计数。 */
    @Query("SELECT COUNT(*) FROM card_collections_cache")
    fun observeRowCount(): Flow<Int>

    /**
     * [T16c] 从未上云的名片夹（isLocalOnly=1，或历史版本创建失败遗留的 serverId=NULL 行）。
     * SyncEngine.backfillLocalOnlyCreates 补建 CREATE op 用。
     */
    @Query("SELECT * FROM card_collections_cache WHERE isLocalOnly = 1 OR serverId IS NULL ORDER BY id ASC")
    suspend fun getNeverSyncedCollectionsOnce(): List<CardCollectionCacheEntity>
}