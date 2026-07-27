package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.ScanResultDao
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }`。
 */
class CollectionRepositoryImpl(
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val scanResultDao: ScanResultDao,
    private val contactCacheDao: ContactCacheDao,
) : CollectionRepository {

    private val collectionMutex = Mutex()

    // ========== 名片夹操作 ==========

    override fun getAllCollections(): Flow<List<CardCollectionCacheEntity>> =
        cardCollectionCacheDao.getAllCollections()

    override suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity> = withContext(Dispatchers.IO) {
        cardCollectionCacheDao.getAllCollectionsOnce()
    }

    override suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity> =
        withContext(Dispatchers.IO) {
            contactCacheDao.getContactsByCollectionOnce(collectionId)
        }

    override fun getCollectionsWithCount(): Flow<List<top.mcxiafeng.badger.data.CardCollectionWithCount>> =
        cardCollectionCacheDao.getCollectionsWithCount()

    override suspend fun getCollectionById(id: Long): CardCollectionCacheEntity? = withContext(Dispatchers.IO) {
        cardCollectionCacheDao.getCollectionById(id)
    }

    override suspend fun insertCollection(collection: CardCollectionCacheEntity): Long = withContext(Dispatchers.IO) {
        cardCollectionCacheDao.insertCollection(collection)
    }

    override suspend fun updateCollection(collection: CardCollectionCacheEntity) = collectionMutex.withLock {
        Log.d("Tester", "updateCollection: id=${collection.id}, name=${collection.name}, dominantColor=${collection.dominantColor}")
        withContext(Dispatchers.IO) {
            cardCollectionCacheDao.updateCollection(collection)
        }
    }

    override suspend fun deleteCollection(collection: CardCollectionCacheEntity) = withContext(Dispatchers.IO) {
        cardCollectionCacheDao.updateCollection(collection.copy(coverAvatarUrl = null))
    }

    override fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>> {
        return contactCacheDao.getContactsByCollection(collectionId)
    }

    // ========== 扫描记录操作 ==========

    override fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>> {
        return scanResultDao.getScanResultsByContact(contactId)
    }

    override fun getContactCollectionIds(contactId: Long): Flow<List<Long>> {
        return scanResultDao.getContactCollectionIds(contactId)
    }

    override suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        rawData: String?,
        ocrText: String?,
        qrCodeContent: String?
    ): Unit = withContext(Dispatchers.IO) {
        val result = ScanResult(
            contactId = contactId,
            collectionId = collectionId,
            sourceType = sourceType,
            rawData = rawData,
            ocrText = ocrText,
            qrCodeContent = qrCodeContent
        )
        scanResultDao.insertScanResult(result)
        Log.d(TAG, "addContactToCollection: contact=$contactId -> collection=$collectionId source=$sourceType")
    }

    override suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean =
        withContext(Dispatchers.IO) { scanResultDao.existsContactInCollection(contactId, collectionId) }

    override suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) = withContext(Dispatchers.IO) {
        scanResultDao.deleteScanResultsByContactAndCollection(contactId, collectionId)
    }

    override suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext
        scanResultDao.deleteScanResultsByContactsAndCollection(contactIds, collectionId)
    }

    override suspend fun getScanRecordCountsByCollection(collectionId: Long): Map<Long, Int> = withContext(Dispatchers.IO) {
        scanResultDao.getScanRecordCountsByCollection(collectionId)
    }

    private companion object {
        const val TAG = "CollectionRepository"
    }
}