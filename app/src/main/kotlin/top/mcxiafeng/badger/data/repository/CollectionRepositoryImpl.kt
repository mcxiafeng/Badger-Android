package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.CardCollection
import top.mcxiafeng.badger.data.CardCollectionDao
import top.mcxiafeng.badger.data.CollectionWithCount
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ScanResult
import top.mcxiafeng.badger.data.ScanResultDao
import javax.inject.Inject

class CollectionRepositoryImpl @Inject constructor(
    private val collectionDao: CardCollectionDao,
    private val scanResultDao: ScanResultDao,
    private val contactDao: ContactDao
) : CollectionRepository {

    private val collectionMutex = Mutex()

    // ========== 名片夹操作 ==========

    override fun getAllCollections(): Flow<List<CardCollection>> = collectionDao.getAllCollections()

    override suspend fun getAllCollectionsOnce(): List<CardCollection> = withContext(Dispatchers.IO) {
        collectionDao.getAllCollectionsOnce()
    }

    override suspend fun getContactsByCollectionOnce(collectionId: Long): List<Contact> = withContext(Dispatchers.IO) {
        contactDao.getContactsByCollectionOnce(collectionId)
    }

    override fun getCollectionsWithCount(): Flow<List<CollectionWithCount>> = collectionDao.getCollectionsWithCount()

    override suspend fun getCollectionById(id: Long): CardCollection? = withContext(Dispatchers.IO) {
        collectionDao.getCollectionById(id)
    }

    override suspend fun insertCollection(collection: CardCollection): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(collection)
    }

    override suspend fun updateCollection(collection: CardCollection) = collectionMutex.withLock {
        Log.d("Tester", "updateCollection: id=${collection.id}, name=${collection.name}, dominantColor=${collection.dominantColor}")
        withContext(Dispatchers.IO) {
            collectionDao.updateCollection(collection)
        }
    }

    override suspend fun deleteCollection(collection: CardCollection) = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(collection)
    }

    override fun getContactsByCollection(collectionId: Long): Flow<List<Contact>> {
        return contactDao.getContactsByCollection(collectionId)
    }

    // ========== 扫描记录操作 ==========

    override fun getScanResultsByContact(contactId: Long): Flow<List<ScanResult>> {
        return scanResultDao.getScanResultsByContact(contactId)
    }

    override suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
        styleColor: Long?,
        rawData: String?,
        ocrText: String?,
        qrCodeContent: String?
    ) = withContext(Dispatchers.IO) {
        val result = ScanResult(
            contactId = contactId,
            collectionId = collectionId,
            sourceType = sourceType,
            styleColor = styleColor,
            rawData = rawData,
            ocrText = ocrText,
            qrCodeContent = qrCodeContent
        )
        scanResultDao.insertScanResult(result)
    }

    override suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean =
        withContext(Dispatchers.IO) { scanResultDao.existsContactInCollection(contactId, collectionId) }

    override suspend fun deleteScanResultById(id: Long) = withContext(Dispatchers.IO) {
        scanResultDao.deleteScanResultById(id)
    }

    override suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) = withContext(Dispatchers.IO) {
        scanResultDao.deleteScanResultsByContactAndCollection(contactId, collectionId)
    }

    override suspend fun getStyleCountsByCollection(collectionId: Long): Map<Long, Int> = withContext(Dispatchers.IO) {
        scanResultDao.getStyleCountsByCollection(collectionId)
    }
}
