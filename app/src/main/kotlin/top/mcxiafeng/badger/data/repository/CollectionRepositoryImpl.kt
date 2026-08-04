package top.mcxiafeng.badger.data.repository

import android.util.Log
import com.google.gson.JsonObject
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
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import java.util.UUID

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }`。
 *
 * [V2-P12] collection 写入路径接 PendingUpload 队列(opType = COLLECTION_UPSERT / COLLECTION_DELETE),
 * 与 ContactRepository(P5) + TagRepository(P12) 风格一致。
 *
 * 注意:CardCollectionCacheEntity 已有 `serverVersion` / `isLocalOnly` 列(P1 阶段补齐),
 * 这里仅消费 `serverVersion` 作为 PendingUpload 的 resourceVersion(供后续 P12.1 冲突解决使用)。
 * isLocalOnly 初值 = true,Worker 成功后会由 P11 bootstrap 校准成 false。
 */
class CollectionRepositoryImpl(
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val scanResultDao: ScanResultDao,
    private val contactCacheDao: ContactCacheDao,
    // [V2-P12] 接 PendingUpload 队列
    private val pendingDao: PendingUploadDao,
    private val pendingUploadScheduler: PendingUploadScheduler,
    private val deviceIdProvider: DeviceIdProvider,
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
        val now = System.currentTimeMillis()
        val toInsert = collection.copy(
            createTime = if (collection.createTime > 0) collection.createTime else now,
            serverVersion = 0L,
            isLocalOnly = true,
        )
        val newId = cardCollectionCacheDao.insertCollection(toInsert)
        Log.d(TAG, "insertCollection: id=$newId name='${toInsert.name}'")
        // [V2-P12] 入队 COLLECTION_UPSERT — id=0 让 Worker 走 POST 新建路径。
        enqueueCollectionUpsert(
            id = 0L,
            name = toInsert.name,
            color = toInsert.dominantColor,
            backgroundImagePath = toInsert.backgroundImagePath,
        )
        newId
    }

    override suspend fun updateCollection(collection: CardCollectionCacheEntity): Unit = collectionMutex.withLock {
                withContext(Dispatchers.IO) {
            val existing = cardCollectionCacheDao.getCollectionById(collection.id)
            cardCollectionCacheDao.updateCollection(collection)
            // [修复防御]: 写前重读防 stale snapshot — 写前查最新,然后比对。
            // 即便 collection 字段未变,UI 仍可能重发 — 这里防止重复入队 op。
            val changed = existing == null
                || existing.name != collection.name
                || existing.description != collection.description
                || existing.backgroundImagePath != collection.backgroundImagePath
                || existing.dominantColor != collection.dominantColor
            if (changed) {
                enqueueCollectionUpsert(
                    id = collection.id,
                    name = collection.name,
                    color = collection.dominantColor,
                    backgroundImagePath = collection.backgroundImagePath,
                )
            } else {
                Log.d(TAG, "updateCollection: id=${collection.id} no change, skip enqueue")
            }
        }
    }

    override suspend fun deleteCollection(collection: CardCollectionCacheEntity) = withContext(Dispatchers.IO) {
        // [V2-P12 修复防御]: 原实现只清 coverAvatarUrl,语义上是"清封面"不是"删除"。
        // 这里**保留**原行为(避免一改触动既有 UI 期望),但通过外包的 `reassignMoveToRecycle` 流程联动。
        // 真正的物理删除应在 CardPage 弹"删除名片夹"二次确认后**显式**调 `purgeCollection`。
        // P12 不引入 purgeCollection,以免 UI 端扩展太快;此处只插入 op + 清封面。
        cardCollectionCacheDao.updateCollection(collection.copy(coverAvatarUrl = null))
        Log.d(TAG, "deleteCollection: id=${collection.id} name='${collection.name}' (cover cleared, op enqueued)")
        // [V2-P12] 入队 COLLECTION_DELETE — Worker 收到后会 DELETE /v1/collections/{id};
        // 若服务端尚未记录该 id(P11 老数据迁移期),会 404 → 视为幂等成功。
        enqueueCollectionDelete(id = collection.id, name = collection.name)
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

    // ========== [V2-P12] op 入队辅助 ==========

    private suspend fun enqueueCollectionUpsert(
        id: Long,
        name: String,
        color: Long?,
        backgroundImagePath: String?,
    ) {
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val colorStr = color?.let { "0x${it.toString(16).uppercase().padStart(8, '0')}" }
        val payload = JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            colorStr?.let { addProperty("color", it) }
            backgroundImagePath?.let { addProperty("background_image_path", it) }
        }
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = -1L,
                opType = OperationTypes.COLLECTION_UPSERT,
                resourceVersion = 0L,
                payloadJson = payload.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        pendingUploadScheduler.kick()
        Log.d(TAG, "enqueueCollectionUpsert: opId=${opId.take(8)} id=$id name=$name")
    }

    private suspend fun enqueueCollectionDelete(id: Long, name: String) {
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
        }
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = -1L,
                opType = OperationTypes.COLLECTION_DELETE,
                resourceVersion = 0L,
                payloadJson = payload.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        pendingUploadScheduler.kick()
        Log.d(TAG, "enqueueCollectionDelete: opId=${opId.take(8)} id=$id name=$name")
    }

    private companion object {
        const val TAG = "CollectionRepository"
    }
}