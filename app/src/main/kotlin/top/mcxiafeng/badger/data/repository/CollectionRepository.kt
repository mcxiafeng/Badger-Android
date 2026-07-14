package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ScanResult

/**
 * 名片夹数据仓库接口
 *
 * 管理名片夹和扫描记录的操作。
 */
interface CollectionRepository {

    // ========== 名片夹操作 ==========

    fun getAllCollections(): Flow<List<CardCollection>>

    suspend fun getAllCollectionsOnce(): List<CardCollection>

    suspend fun getContactsByCollectionOnce(collectionId: Long): List<Contact>

    fun getCollectionsWithCount(): Flow<List<CollectionWithCount>>

    suspend fun getCollectionById(id: Long): CardCollection?

    suspend fun insertCollection(collection: CardCollection): Long

    suspend fun updateCollection(collection: CardCollection)

    suspend fun deleteCollection(collection: CardCollection)

    fun getContactsByCollection(collectionId: Long): Flow<List<Contact>>

    // ========== 扫描记录操作 ==========

    fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>>

    /**
     * 获取指定联系人所属的所有名片夹 ID（只取 collectionId 列，比 getScanResultsByContact 更轻量）。
     */
    fun getContactCollectionIds(contactId: Long): Flow<List<Long>>

    /**
     * v5 schema 移除 ScanResult.styleColor 后,样式由 Tag.color 表达;
     * 本方法不再接收任何样式参数。
     */
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
