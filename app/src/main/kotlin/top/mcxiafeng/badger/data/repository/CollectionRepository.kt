package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.CardCollectionWithCount
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * 名片夹数据仓库接口。
 *
 * [A3] 输出 V2 cache entity(`CardCollectionCacheEntity` / `ContactCacheEntity`)
 * 与 `CardCollectionWithCount` 包装类。
 *
 * [Phase 4 Task #20] 退役 `scan_results` 表，成员关联改走 `collection_member_cache`。
 * 扫码元数据（rawData/ocrText/qrCodeContent/confidence）不再保留，服务端已接管。
 */
interface CollectionRepository {

    // ========== 名片夹操作 ==========

    fun getAllCollections(): Flow<List<CardCollectionCacheEntity>>

    suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity>

    suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity>

    fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>>

    suspend fun getCollectionById(id: Long): CardCollectionCacheEntity?

    suspend fun insertCollection(collection: CardCollectionCacheEntity): Long

    suspend fun updateCollection(collection: CardCollectionCacheEntity)

    suspend fun deleteCollection(collection: CardCollectionCacheEntity)

    fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>>

    // ========== 成员关联操作 ==========

    /** 获取指定联系人所属的所有名片夹 ID 列表 */
    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    /** 将联系人添加到名片夹 */
    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String = "manual",
    )

    /** 检查联系人是否已在名片夹中 */
    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    /** 从名片夹移除联系人 */
    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long)

    /** 批量从名片夹移除联系人 */
    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long)

    /** 按名片夹统计每个联系人的成员记录数 */
    suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int>
}
