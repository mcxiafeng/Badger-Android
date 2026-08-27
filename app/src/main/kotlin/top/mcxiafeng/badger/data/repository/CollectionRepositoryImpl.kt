package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.CollectionMemberCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.CollectionMemberCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.network.ServerApi

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::CollectionRepositoryImpl) { bind<CollectionRepository>() }`。
 *
 * [Phase 3] 直推改造：写操作（insert / update / delete / 成员关联）本地落
 * `card_collections_cache` / `collection_member_cache` 后**直推** `/api/user/collections` 新契约
 * （uuid / personMembers + 成员子接口），不再走 PendingUpload 队列。
 *
 * [Phase 4 Task #20] 退役 `scan_results` 表，成员关联改走 `collection_member_cache`。
 * 扫码元数据（rawData/ocrText/qrCodeContent/confidence）不再保留，服务端已接管。
 *
 * 关键语义：
 * - `id:Long` → `serverId:uuid`（服务端分配，回填本列）；
 * - 封面背景：本地 `backgroundImagePath`（磁盘文件）与服务端 `backgroundURL`（远端 URL）
 *   双轨；直推仅用 coverAvatarUrl 有值时的远端 URL，本地路径不推服务端。
 * - 成员关联：本地 `collection_member_cache` + 直推成员子接口
 *   （POST/DELETE `/collections/{uuid}/members/{personUuid}`）。
 *
 * [修复防御]：直推失败**不阻塞本地保存**（本地最终一致，sync 兜底），但必须打日志。
 *
 * 注意：deleteCollection 保留原"清封面"语义（由 `reassignMoveToRecycle` 流程联动真正物理删除），
 * 这里仅清封面 + 直推 DELETE。
 */
class CollectionRepositoryImpl(
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val collectionMemberCacheDao: CollectionMemberCacheDao,
    private val contactCacheDao: ContactCacheDao,
    // [Phase 3] 直推新 Java /api 契约
    private val serverApi: ServerApi,
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

    /**
     * 新建名片夹：本地插入 + 直推 `POST /api/user/collections`（uuid 回填）。
     * 离线直推失败 → 本地 `isLocalOnly=true` 兜底（下次编辑 create-on-push 补推）。
     */
    override suspend fun insertCollection(collection: CardCollectionCacheEntity): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val toInsert = collection.copy(
            createTime = if (collection.createTime > 0) collection.createTime else now,
            isLocalOnly = true,
        )
        val newId = cardCollectionCacheDao.insertCollection(toInsert)
        Log.d(TAG, "insertCollection: id=$newId name='${toInsert.name}'")
        // [Phase 3] 直推 create → 服务端分配 uuid
        val serverUuid = try {
            serverApi.createCollection(
                name = toInsert.name,
                description = toInsert.description,
                backgroundURL = toInsert.coverAvatarUrl,
                personMembers = null,
            )
        } catch (e: Exception) {
            Log.w(TAG, "insertCollection: createCollection 失败,落本地 isLocalOnly 兜底 id=$newId", e)
            null
        }
        if (serverUuid != null) {
            cardCollectionCacheDao.updateCollection(toInsert.copy(id = newId, serverId = serverUuid, isLocalOnly = false))
        }
        newId
    }

    override suspend fun updateCollection(collection: CardCollectionCacheEntity): Unit = collectionMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = cardCollectionCacheDao.getCollectionById(collection.id)
            cardCollectionCacheDao.updateCollection(collection)
            // 写前重读防 stale snapshot — 即使字段未变,UI 仍可能重发,这里只推实际变化
            val changed = existing == null
                || existing.name != collection.name
                || existing.description != collection.description
                || existing.backgroundImagePath != collection.backgroundImagePath
                || existing.dominantColor != collection.dominantColor
            if (changed) {
                pushCollectionPatch(collection)
            } else {
                Log.d(TAG, "updateCollection: id=${collection.id} no change, skip push")
            }
        }
    }

    override suspend fun deleteCollection(collection: CardCollectionCacheEntity): Unit = withContext(Dispatchers.IO) {
        // 保留原"清封面"语义（物理删除由 reassignMoveToRecycle 流程联动）
        cardCollectionCacheDao.updateCollection(collection.copy(coverAvatarUrl = null))
        Log.d(TAG, "deleteCollection: id=${collection.id} name='${collection.name}' (cover cleared)")
        // [Phase 3] 直推 DELETE（404 幂等成功由 ServerApi 处理）
        val uuid = collection.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteCollection(uuid)
            } catch (e: Exception) {
                Log.w(TAG, "deleteCollection: DELETE collection $uuid 失败(本地已清)", e)
            }
        } else {
            Log.w(TAG, "deleteCollection: id=${collection.id} isLocalOnly(无 serverId),仅本地处理")
        }
    }

    override fun getContactsByCollection(collectionId: Long): Flow<List<ContactCacheEntity>> {
        return contactCacheDao.getContactsByCollection(collectionId)
    }

    // ========== 成员关联操作 ==========

    override fun getContactCollectionIds(contactId: Long): Flow<List<Long>> {
        return collectionMemberCacheDao.observeCollectionIdsByContact(contactId)
    }

    override suspend fun addContactToCollection(
        contactId: Long,
        collectionId: Long,
        sourceType: String,
    ): Unit = withContext(Dispatchers.IO) {
        val member = CollectionMemberCacheEntity(
            contactId = contactId,
            collectionId = collectionId,
        )
        collectionMemberCacheDao.insert(member)
        Log.d(TAG, "addContactToCollection: contact=$contactId -> collection=$collectionId source=$sourceType")
        // [Phase 3] 直推成员子接口（本地 collection_member_cache + 服务端 personMembers 双轨）
        pushCollectionMemberAdd(collectionId, contactId)
    }

    override suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean =
        withContext(Dispatchers.IO) { collectionMemberCacheDao.exists(contactId, collectionId) }

    override suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) = withContext(Dispatchers.IO) {
        collectionMemberCacheDao.delete(contactId, collectionId)
        pushCollectionMemberRemove(collectionId, contactId)
    }

    override suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty()) return@withContext
        collectionMemberCacheDao.deleteByContactsAndCollection(contactIds, collectionId)
        contactIds.forEach { pushCollectionMemberRemove(collectionId, it) }
    }

    override suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int> = withContext(Dispatchers.IO) {
        collectionMemberCacheDao.getMemberCountsByCollection(collectionId)
    }

    // ========== [Phase 3] 直推辅助 ==========

    /** 直推 `PUT /api/user/collections/{uuid}`，仅传非空字段。 */
    private suspend fun pushCollectionPatch(collection: CardCollectionCacheEntity) {
        val uuid = collection.serverId?.takeIf { it.isNotBlank() }
        if (uuid == null) {
            Log.w(TAG, "pushCollectionPatch: id=${collection.id} 无 serverId,跳过(待 create-on-push)")
            return
        }
        try {
            serverApi.patchCollection(
                uuid = uuid,
                name = collection.name,
                description = collection.description,
                backgroundURL = collection.coverAvatarUrl,
            )
        } catch (e: Exception) {
            Log.w(TAG, "pushCollectionPatch: PUT collection $uuid 失败(本地已保存)", e)
        }
    }

    /** 直推加成员 `POST /collections/{uuid}/members/{personUuid}`。缺 uuid 跳过，失败仅日志。 */
    private suspend fun pushCollectionMemberAdd(collectionId: Long, contactId: Long) {
        val colUuid = cardCollectionCacheDao.getCollectionById(collectionId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactCacheDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addCollectionMember(colUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushCollectionMemberAdd: add member 失败(本地已存,sync 兜底) col=$colUuid person=$personUuid", e)
        }
    }

    /** 直推移除成员 `DELETE /collections/{uuid}/members/{personUuid}`。 */
    private suspend fun pushCollectionMemberRemove(collectionId: Long, contactId: Long) {
        val colUuid = cardCollectionCacheDao.getCollectionById(collectionId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactCacheDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.removeCollectionMember(colUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushCollectionMemberRemove: remove member 失败(本地已删,sync 兜底) col=$colUuid person=$personUuid", e)
        }
    }

    private companion object {
        const val TAG = "CollectionRepository"
    }
}
