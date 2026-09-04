package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
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
import top.mcxiafeng.badger.data.model.CardCollectionWithCount
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.RemoteIdentity
import top.mcxiafeng.badger.sync.identity
import top.mcxiafeng.badger.sync.rebaseCollection
import top.mcxiafeng.badger.shared.util.randomUuid

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

    override suspend fun getAllCollectionsOnce(): List<CardCollectionCacheEntity> = withContext(BadgerDispatchers.io) {
        cardCollectionCacheDao.getAllCollectionsOnce()
    }

    override suspend fun getContactsByCollectionOnce(collectionId: Long): List<ContactCacheEntity> =
        withContext(BadgerDispatchers.io) {
            contactCacheDao.getContactsByCollectionOnce(collectionId)
        }

    override fun getCollectionsWithCount(): Flow<List<CardCollectionWithCount>> =
        cardCollectionCacheDao.getCollectionsWithCount()

    override suspend fun getCollectionById(id: Long): CardCollectionCacheEntity? = withContext(BadgerDispatchers.io) {
        cardCollectionCacheDao.getCollectionById(id)
    }

    /**
     * 新建名片夹（[T14] 本地权威写路径）：生成 clientUuid → 本地落 `PendingCreate` 行 →
     * CREATE op 入队 + kick。实际 POST 由 SyncEngine.createOnPush 重放时执行。
     */
    override suspend fun insertCollection(collection: CardCollectionCacheEntity): Long = withContext(BadgerDispatchers.io) {
        val now = System.currentTimeMillis()
        val clientUuid = randomUuid()
        val toInsert = collection.copy(
            createTime = if (collection.createTime > 0) collection.createTime else now,
            serverId = clientUuid,
            isLocalOnly = true,
        )
        val newId = cardCollectionCacheDao.insertCollection(toInsert)
        BadgerLog.d(TAG, "insertCollection: id=$newId name='${toInsert.name}'")
        try {
            serverApi.enqueueCreateCollection(
                localId = newId,
                name = toInsert.name,
                description = toInsert.description,
                backgroundURL = toInsert.coverAvatarUrl,
                clientUuid = clientUuid,
            )
        } catch (e: Exception) {
            BadgerLog.w(TAG, "insertCollection: CREATE 入队失败(本地已保存,待 syncOnce 回填) id=$newId", e)
        }
        newId
    }

    override suspend fun updateCollection(collection: CardCollectionCacheEntity): Unit = collectionMutex.withLock {
        withContext(BadgerDispatchers.io) {
            val existing = cardCollectionCacheDao.getCollectionById(collection.id)
            // [F3/T08] 投影实体不带 identity 字段，全行 @Update 会抹掉身份字段；
            // 写前强制走 IdentityRebase（投影 → 实体的唯一合法路径）。
            val rebased = existing?.let { rebaseCollection(collection, it) } ?: collection
            cardCollectionCacheDao.updateCollection(rebased)
            // 写前重读防 stale snapshot — 即使字段未变,UI 仍可能重发,这里只推实际变化
            val changed = existing == null
                || existing.name != rebased.name
                || existing.description != rebased.description
                || existing.backgroundImagePath != rebased.backgroundImagePath
                || existing.dominantColor != rebased.dominantColor
            if (changed) {
                pushCollectionPatch(rebased)
            } else {
                BadgerLog.d(TAG, "updateCollection: id=${rebased.id} no change, skip push")
            }
        }
    }

    override suspend fun deleteCollection(collection: CardCollectionCacheEntity): Unit = withContext(BadgerDispatchers.io) {
        // [F3/T08] 调用方可能传投影实体（deleteCollection(CollectionWithCount)），先 rebase
        val existing = cardCollectionCacheDao.getCollectionById(collection.id)
        val rebased = existing?.let { rebaseCollection(collection, it) } ?: collection
        // 保留原"清封面"语义（物理删除由 reassignMoveToRecycle 流程联动）
        cardCollectionCacheDao.updateCollection(rebased.copy(coverAvatarUrl = null))
        BadgerLog.d(TAG, "deleteCollection: id=${rebased.id} name='${rebased.name}' (cover cleared)")
        // [Phase 3] DELETE 入队 + kick（404 幂等成功由重放侧处理）
        val uuid = rebased.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteCollection(rebased.id, uuid)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "deleteCollection: DELETE 入队失败(本地已清)", e)
            }
        } else {
            BadgerLog.w(TAG, "deleteCollection: id=${rebased.id} isLocalOnly(无 serverId),仅本地处理")
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
    ): Unit = withContext(BadgerDispatchers.io) {
        val member = CollectionMemberCacheEntity(
            contactId = contactId,
            collectionId = collectionId,
        )
        collectionMemberCacheDao.insert(member)
        BadgerLog.d(TAG, "addContactToCollection: contact=$contactId -> collection=$collectionId source=$sourceType")
        // [Phase 3] 直推成员子接口（本地 collection_member_cache + 服务端 personMembers 双轨）
        pushCollectionMemberAdd(collectionId, contactId)
    }

    override suspend fun existsContactInCollection(contactId: Long, collectionId: Long): Boolean =
        withContext(BadgerDispatchers.io) { collectionMemberCacheDao.exists(contactId, collectionId) }

    override suspend fun removeContactFromCollection(contactId: Long, collectionId: Long) = withContext(BadgerDispatchers.io) {
        collectionMemberCacheDao.delete(contactId, collectionId)
        pushCollectionMemberRemove(collectionId, contactId)
    }

    override suspend fun removeContactsFromCollection(contactIds: List<Long>, collectionId: Long) = withContext(BadgerDispatchers.io) {
        if (contactIds.isEmpty()) return@withContext
        collectionMemberCacheDao.deleteByContactsAndCollection(contactIds, collectionId)
        contactIds.forEach { pushCollectionMemberRemove(collectionId, it) }
    }

    override suspend fun getMemberCountsByCollection(collectionId: Long): Map<Long, Int> = withContext(BadgerDispatchers.io) {
        collectionMemberCacheDao.getMemberCountsByCollection(collectionId)
    }

    // ========== [Phase 3] 直推辅助 ==========

    /**
     * [T14] 确保名片夹的 CREATE 意图已入队，返回 PATCH/MEMBER 可用的 remoteId。
     * Synced → serverId；PendingCreate → 复用 clientUuid；Unidentified（存量行）→ 现场生成落盘。
     */
    private suspend fun ensureCollectionCreateEnqueued(collectionId: Long): String? {
        val collection = cardCollectionCacheDao.getCollectionById(collectionId) ?: return null
        val identity = collection.identity()
        val remoteId = when (identity) {
            is RemoteIdentity.Synced -> identity.serverId
            is RemoteIdentity.PendingCreate -> identity.clientUuid
            is RemoteIdentity.Unidentified -> randomUuid()
        }
        if (identity is RemoteIdentity.Unidentified) {
            cardCollectionCacheDao.updateCollection(collection.copy(serverId = remoteId, isLocalOnly = true))
        }
        if (identity !is RemoteIdentity.Synced) {
            try {
                serverApi.enqueueCreateCollection(
                    localId = collection.id,
                    name = collection.name,
                    description = collection.description,
                    backgroundURL = collection.coverAvatarUrl,
                    clientUuid = remoteId,
                )
            } catch (e: Exception) {
                BadgerLog.w(TAG, "ensureCollectionCreateEnqueued: collectionId=$collectionId CREATE 入队失败(本地已保存)", e)
            }
        }
        return remoteId
    }

    /** [T12b] PATCH 入队 + kick。PendingCreate 先确保 CREATE 入队，remoteId 暂用 clientUuid。 */
    private suspend fun pushCollectionPatch(collection: CardCollectionCacheEntity) {
        val remoteId = ensureCollectionCreateEnqueued(collection.id) ?: return
        try {
            serverApi.patchCollection(
                localId = collection.id,
                uuid = remoteId,
                name = collection.name,
                description = collection.description,
                backgroundURL = collection.coverAvatarUrl,
            )
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushCollectionPatch: collection=${collection.id} 入队失败(本地已保存)", e)
        }
    }

    /** [T12b] MEMBER_ADD 入队 + kick。PendingCreate 先确保 CREATE 入队。 */
    private suspend fun pushCollectionMemberAdd(collectionId: Long, contactId: Long) {
        val colUuid = ensureCollectionCreateEnqueued(collectionId) ?: return
        val personUuid = contactCacheDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addCollectionMember(collectionId, colUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushCollectionMemberAdd: add member 入队失败(本地已存,sync 兜底) col=$colUuid person=$personUuid", e)
        }
    }

    /** [T12b] MEMBER_REMOVE 入队 + kick。PendingCreate 先确保 CREATE 入队。 */
    private suspend fun pushCollectionMemberRemove(collectionId: Long, contactId: Long) {
        val colUuid = ensureCollectionCreateEnqueued(collectionId) ?: return
        val personUuid = contactCacheDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.removeCollectionMember(collectionId, colUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushCollectionMemberRemove: remove member 入队失败(本地已删,sync 兜底) col=$colUuid person=$personUuid", e)
        }
    }

    private companion object {
        const val TAG = "CollectionRepository"
    }
}
