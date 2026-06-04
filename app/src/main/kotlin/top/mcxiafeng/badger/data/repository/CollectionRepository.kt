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

    suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        styleColor: Long? = null,
        rawData: String? = null,
        ocrText: String? = null,
        qrCodeContent: String? = null
    )

    suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean

    suspend fun deleteScanResultById(id: Long)

    suspend fun removeContactFromCollection(contactId: Long, collectionId: Long)

    suspend fun getStyleCountsByCollection(collectionId: Long): Map<Long, Int>
}
