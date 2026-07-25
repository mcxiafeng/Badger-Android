package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.CardCollectionWithCount
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * 名片夹数据仓库接口。
 *
 * [A3] 输出 V2 cache entity(`CardCollectionCacheEntity` / `ContactCacheEntity`)
 * 与 `CardCollectionWithCount` 包装类。
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

    // ========== 扫描记录操作 ==========

    fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>>

    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        rawData: String? = null,
        ocrText: String? = null,
        qrCodeContent: String? = null
    )

    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long)

    suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long)

    suspend fun getScanRecordCountsByCollection(collectionId: Long): Map<Long, Int>
}