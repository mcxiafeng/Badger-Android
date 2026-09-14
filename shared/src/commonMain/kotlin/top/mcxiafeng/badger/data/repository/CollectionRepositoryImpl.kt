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
import top.mcxiafeng.badger.shared.util.nowMs

class CollectionRepositoryImpl(
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val collectionMemberCacheDao: CollectionMemberCacheDao,
    private val contactCacheDao: ContactCacheDao,
    
    private val serverApi: ServerApi,
) : CollectionRepository {

    private val collectionMutex = Mutex()

    

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

    

    override suspend fun insertCollection(collection: CardCollectionCacheEntity): Long = withContext(BadgerDispatchers.io) {
        val now = nowMs()
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
            
            
            val rebased = existing?.let { rebaseCollection(collection, it) } ?: collection
            cardCollectionCacheDao.updateCollection(rebased)
            
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
        
        val existing = cardCollectionCacheDao.getCollectionById(collection.id)
        val rebased = existing?.let { rebaseCollection(collection, it) } ?: collection
        
        cardCollectionCacheDao.updateCollection(rebased.copy(coverAvatarUrl = null))
        BadgerLog.d(TAG, "deleteCollection: id=${rebased.id} name='${rebased.name}' (cover cleared)")
        
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

    
    private suspend fun pushCollectionMemberAdd(collectionId: Long, contactId: Long) {
        val colUuid = ensureCollectionCreateEnqueued(collectionId) ?: return
        val personUuid = contactCacheDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addCollectionMember(collectionId, colUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushCollectionMemberAdd: add member 入队失败(本地已存,sync 兜底) col=$colUuid person=$personUuid", e)
        }
    }

    
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
