package top.mcxiafeng.badger.sync

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.shared.db.dbTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactFieldValueCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.PersonProfileCacheEntity
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.data.repository.ContactMapper
import top.mcxiafeng.badger.data.repository.ContactMapper.buildProfileDto
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPersonProfileEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformRows
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformsJson
import top.mcxiafeng.badger.data.repository.UserProfileRepository
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.CollectionDto
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.TagDto
import top.mcxiafeng.badger.network.parseServerDateMillis
import top.mcxiafeng.badger.shared.util.PinyinUtils
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.shared.util.randomUuid
import kotlinx.atomicfu.atomic
import top.mcxiafeng.badger.shared.util.deleteFileQuietly

class SyncEngine(
    private val serverApi: ServerApi,
    private val outboxStore: OutboxQueue,
    private val db: AppDatabase,
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val tagCacheDao: TagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val personProfileCacheDao: PersonProfileCacheDao,
    private val userProfileRepository: UserProfileRepository,
) {

    private val syncMutex = Mutex()
    private val started = atomic(false)

    

    
    suspend fun syncOnce(): SyncOnceResult = withContext(BadgerDispatchers.io) {
        syncMutex.withLock {
            backfillLocalOnlyCreates()
            val push = pushLocked(includeBackoff = true)
            val pull = doPull()
            SyncOnceResult(pushedOps = push.pushedOps, pull = pull)
        }
    }

    
    suspend fun syncOnceIfIdle(): SyncOnceResult = withContext(BadgerDispatchers.io) {
        if (!started.compareAndSet(false, true)) {
            BadgerLog.d(TAG, "syncOnceIfIdle: 已在同步中,跳过")
            return@withContext SyncOnceResult(pushedOps = 0, pull = SyncPullResult.Skipped)
        }
        try {
            syncMutex.withLock {
                backfillLocalOnlyCreates()
                val push = pushLocked()
                val pull = doPull()
                SyncOnceResult(pushedOps = push.pushedOps, pull = pull)
            }
        } finally {
            started.value = false
        }
    }

    

    suspend fun pushOnce(includeBackoff: Boolean = false): PushOutcome = withContext(BadgerDispatchers.io) {
        syncMutex.withLock { pushLocked(includeBackoff) }
    }

    
    suspend fun pullOnce(): SyncPullResult = withContext(BadgerDispatchers.io) {
        syncMutex.withLock { doPull() }
    }

    

    private suspend fun pushLocked(includeBackoff: Boolean = false): PushOutcome {
        var pushedOps = 0
        var failedOps = 0
        while (true) {
            val ready = outboxStore.getReady(includeBackoff = includeBackoff)
            if (ready.isEmpty()) break
            var progressed = false
            
            for (op in ready.sortedBy { it.op.pushPriority() }) {
                val outcome = replayOp(op)
                when (outcome) {
                    is OpOutcome.Success -> {
                        outboxStore.markSuccess(op.id)
                        pushedOps++
                        progressed = true
                    }
                    is OpOutcome.Failed -> {
                        
                        outboxStore.recordFailure(op.id, outcome.error)
                        failedOps++
                    }
                    OpOutcome.BlockedOnCreate -> {
                        
                    }
                }
                if (outcome is OpOutcome.Success && op.op == OutboxOpType.CREATE) {
                    
                    
                    break
                }
                if (outcome is OpOutcome.Failed) break 
            }
            if (failedOps > 0) break
            if (!progressed) break 
        }
        if (pushedOps > 0 || failedOps > 0) {
            BadgerLog.d(TAG, "pushOnce: pushed=$pushedOps failed=$failedOps")
        }
        return PushOutcome(pushedOps = pushedOps, failedOps = failedOps)
    }

    private suspend fun replayOp(op: OutboxOp): OpOutcome {
        if (op.op == OutboxOpType.CREATE) {
            return when (val result = createOnPush(op)) {
                
                CommitResult.SentSuccess, is CommitResult.Written, CommitResult.NotFound -> OpOutcome.Success
                is CommitResult.SentFailed -> OpOutcome.Failed(IllegalStateException(result.reason))
            }
        }
        val remoteId = resolveRemoteId(op) ?: return OpOutcome.BlockedOnCreate
        return try {
            BadgerLog.d(
                TAG,
                "replay: id=${op.id} kind=${op.entityKind} op=${op.op} localId=${op.localId} " +
                    "remote=${remoteId.take(8)} attempts=${op.attempts}",
            )
            serverApi.replayOutboxOp(op.copy(remoteId = remoteId))
            OpOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BadgerLog.w(TAG, "replay: id=${op.id} kind=${op.entityKind} op=${op.op} 失败", e)
            OpOutcome.Failed(e)
        }
    }

    

    private suspend fun resolveRemoteId(op: OutboxOp): String? {
        val identity = loadIdentity(op.entityKind, op.localId) ?: return op.remoteId
        return when {
            identity is RemoteIdentity.Synced -> identity.serverId
            identity is RemoteIdentity.PendingCreate && op.op == OutboxOpType.DELETE -> op.remoteId
            identity is RemoteIdentity.PendingCreate -> null
            else -> op.remoteId
        }
    }

    

    

    internal suspend fun createOnPush(op: OutboxOp): CommitResult = try {
        when (op.entityKind) {
            EntityKind.PERSON -> createOnPushPerson(op)
            EntityKind.TAG -> createOnPushTag(op)
            EntityKind.COLLECTION -> createOnPushCollection(op)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        
        BadgerLog.w(TAG, "createOnPush: kind=${op.entityKind} localId=${op.localId} 失败,保留 PendingCreate", e)
        CommitResult.SentFailed(e.message ?: e::class.simpleName ?: "Exception")
    }

    private suspend fun createOnPushPerson(op: OutboxOp): CommitResult {
        val contact = contactCacheDao.getContactById(op.localId) ?: return CommitResult.NotFound
        val identity = contact.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            BadgerLog.d(TAG, "createOnPushPerson: id=${contact.id} 已 Synced,跳过 POST")
            return CommitResult.SentSuccess
        }
        if (identity is RemoteIdentity.Unidentified) {
            contactCacheDao.updateContact(contact.copy(serverId = clientUuid, isLocalOnly = true))
        }
        val platforms = contactPlatformCacheDao.getPlatformsByContact(contact.id)
        val profileEntity = contact.serverId?.let { personProfileCacheDao.getByServerId(it) }
        val serverUuid = serverApi.createPerson(
            contact.name,
            
            buildProfileDto(
                contact, platforms,
                ContactMapper.loadBasicFieldValues(db.contactFieldCacheDao(), db.contactFieldValueCacheDao(), contact.id),
                profileExtra = profileEntity?.extra, backgroundURL = profileEntity?.backgroundURL,
            ),
            clientUuid,
        )
        
        
        val fresh = contactCacheDao.getContactById(contact.id) ?: contact
        contactCacheDao.updateContact(fresh.copy(serverId = serverUuid, isLocalOnly = false))
        outboxStore.backfillAfterCreate(EntityKind.PERSON, contact.id, clientUuid, serverUuid)
        BadgerLog.d(TAG, "createOnPushPerson: id=${contact.id} uuid=${serverUuid.take(8)} name=${contact.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createOnPushTag(op: OutboxOp): CommitResult {
        val tag = tagCacheDao.getTagById(op.localId) ?: return CommitResult.NotFound
        val identity = tag.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            BadgerLog.d(TAG, "createOnPushTag: id=${tag.id} 已 Synced,跳过 POST")
            return CommitResult.SentSuccess
        }
        if (identity is RemoteIdentity.Unidentified) {
            tagCacheDao.updateTag(tag.copy(serverId = clientUuid, isLocalOnly = true))
        }
        val serverUuid = createTagWithUuidFallback(tag, clientUuid)
        tagCacheDao.updateTag(tag.copy(serverId = serverUuid, isLocalOnly = false))
        outboxStore.backfillAfterCreate(EntityKind.TAG, tag.id, clientUuid, serverUuid)
        BadgerLog.d(TAG, "createOnPushTag: id=${tag.id} uuid=${serverUuid.take(8)} name=${tag.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createOnPushCollection(op: OutboxOp): CommitResult {
        val collection = cardCollectionCacheDao.getCollectionById(op.localId) ?: return CommitResult.NotFound
        val identity = collection.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            BadgerLog.d(TAG, "createOnPushCollection: id=${collection.id} 已 Synced,跳过 POST")
            return CommitResult.SentSuccess
        }
        if (identity is RemoteIdentity.Unidentified) {
            cardCollectionCacheDao.updateCollection(collection.copy(serverId = clientUuid, isLocalOnly = true))
        }
        val serverUuid = try {
            serverApi.createCollection(
                collection.name,
                collection.description,
                collection.coverAvatarUrl,
                personMembers = null,
                uuid = clientUuid,
            )
        } catch (e: ApiException) {
            if (e.status != HTTP_BAD_REQUEST) throw e
            
            BadgerLog.e(TAG, "createOnPushCollection: 服务端 400 拒收 uuid,降级去 uuid 重试 name=${collection.name}", e)
            serverApi.createCollection(
                collection.name,
                collection.description,
                collection.coverAvatarUrl,
                personMembers = null,
                uuid = null,
            )
        }
        cardCollectionCacheDao.updateCollection(collection.copy(serverId = serverUuid, isLocalOnly = false))
        outboxStore.backfillAfterCreate(EntityKind.COLLECTION, collection.id, clientUuid, serverUuid)
        BadgerLog.d(TAG, "createOnPushCollection: id=${collection.id} uuid=${serverUuid.take(8)} name=${collection.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createTagWithUuidFallback(tag: TagCacheEntity, clientUuid: String): String = try {
        serverApi.createTag(tag.name, tag.colorHash, personMembers = null, uuid = clientUuid)
    } catch (e: ApiException) {
        if (e.status != HTTP_BAD_REQUEST) throw e
        BadgerLog.e(TAG, "createOnPushTag: 服务端 400 拒收 uuid,降级去 uuid 重试 name=${tag.name}", e)
        serverApi.createTag(tag.name, tag.colorHash, personMembers = null, uuid = null)
    }

    

    private fun resolveCreateUuid(identity: RemoteIdentity): String? = when (identity) {
        is RemoteIdentity.Synced -> null
        is RemoteIdentity.PendingCreate -> identity.clientUuid
        is RemoteIdentity.Unidentified -> randomUuid()
    }

    private suspend fun loadIdentity(kind: EntityKind, localId: Long): RemoteIdentity? = when (kind) {
        EntityKind.PERSON -> contactCacheDao.getContactById(localId)?.identity()
        EntityKind.TAG -> tagCacheDao.getTagById(localId)?.identity()
        EntityKind.COLLECTION -> cardCollectionCacheDao.getCollectionById(localId)?.identity()
    }

    

    

    private suspend fun backfillLocalOnlyCreates(): Int {
        var created = 0
        contactCacheDao.getLocalOnlyContactsOnce().forEach { contact ->
            val profileEntity = contact.serverId?.let { personProfileCacheDao.getByServerId(it) }
            val result = outboxStore.enqueue(
                EntityKind.PERSON, contact.id, contact.serverId, OutboxOpType.CREATE,
                buildJsonObject {
                    put("name", contact.name)
                    put(
                        "profile",
                        buildProfileDto(
                            contact, emptyList(),
                            ContactMapper.loadBasicFieldValues(db.contactFieldCacheDao(), db.contactFieldValueCacheDao(), contact.id),
                            profileExtra = profileEntity?.extra, backgroundURL = profileEntity?.backgroundURL,
                        ).toJsonObject(),
                    )
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        tagCacheDao.getNeverSyncedTagsOnce().forEach { tag ->
            val result = outboxStore.enqueue(
                EntityKind.TAG, tag.id, tag.serverId, OutboxOpType.CREATE,
                buildJsonObject {
                    put("name", tag.name)
                    tag.colorHash?.takeIf { it.isNotBlank() }?.let { put("colorHash", it) }
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        cardCollectionCacheDao.getNeverSyncedCollectionsOnce().forEach { collection ->
            val result = outboxStore.enqueue(
                EntityKind.COLLECTION, collection.id, collection.serverId, OutboxOpType.CREATE,
                buildJsonObject {
                    put("name", collection.name)
                    collection.description?.let { put("description", it) }
                    collection.coverAvatarUrl?.let { put("backgroundURL", it) }
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        if (created > 0) BadgerLog.d(TAG, "backfillLocalOnlyCreates: 补建 $created 条 CREATE op")
        return created
    }

    

    private suspend fun doPull(): SyncPullResult {
        
        
        val knownSelfPersonId = cachedSelfPersonId
            ?: AuthPrefs.readSelfPersonId()?.also { cachedSelfPersonId = it }
        if (knownSelfPersonId != null) purgeStaleSelfContact(knownSelfPersonId)

        var cursor = syncCursorDao.getLastVersion() ?: 0L
        var applied = 0
        var rounds = 0
        var hasMore = true

        while (hasMore && rounds < MAX_PULL_ROUNDS) {
            rounds++
            val page = try {
                serverApi.syncSince(cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BadgerLog.w(TAG, "doPull: syncSince($cursor) 失败 rounds=$rounds", e)
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            if (page.version < cursor) {
                BadgerLog.e(TAG, "doPull: 服务端游标回退 $cursor -> ${page.version}")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }
            if (page.changes.isEmpty()) {
                if (page.hasMore) {
                    BadgerLog.e(TAG, "doPull: 空批次却 hasMore=true, cursor=$cursor version=${page.version}")
                    return SyncPullResult.Failed(applied = applied, cursor = cursor)
                }
                if (page.version != cursor) {
                    BadgerLog.e(TAG, "doPull: 空批次 version 非当前游标 $cursor -> ${page.version}, 拒绝跳跃")
                    return SyncPullResult.Failed(applied = applied, cursor = cursor)
                }
                hasMore = false
                break
            }
            if (page.version == cursor) {
                BadgerLog.e(TAG, "doPull: 有变更但 version 未前进 cursor=$cursor")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            if (!applyChanges(page.changes)) {
                BadgerLog.e(TAG, "doPull: 批次应用失败,游标保持 $cursor,下轮重放 changes=${page.changes.size}")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            applied += page.changes.size
            cursor = page.version
            syncCursorDao.upsert(
                SyncCursorEntity(
                    lastVersion = cursor,
                    updatedAt = nowMs(),
                )
            )
            hasMore = page.hasMore
            BadgerLog.d(TAG, "doPull: 批次完成 changes=${page.changes.size} cursor=$cursor hasMore=$hasMore")
        }

        if (hasMore) {
            BadgerLog.e(TAG, "doPull: 达到最大拉取轮数 $MAX_PULL_ROUNDS, 当前 cursor=$cursor, 仍有 hasMore=true")
            return SyncPullResult.Failed(applied = applied, cursor = cursor)
        }

        BadgerLog.d(TAG, "doPull: 完成 applied=$applied cursor=$cursor rounds=$rounds")
        return SyncPullResult.Done(applied = applied, cursor = cursor)
    }

    
    private suspend fun applyChanges(changes: List<SyncChange>): Boolean {
        for (change in changes) {
            try {
                if (isSelfChange(change)) {
                    
                    
                    
                    
                    applyChange(change)
                } else {
                    
                    db.dbTransaction {
                        applyChange(change)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BadgerLog.e(
                    TAG,
                    "applyChanges: version=${change.version} type=${change.type} object=${change.objectName} failed",
                    e,
                )
                return false
            }
        }
        return true
    }

    
    private suspend fun isSelfChange(change: SyncChange): Boolean {
        if (change.objectName != "Person") return false
        if (isSelfPerson(change.objectId ?: "")) return true
        if (change.type == "ADD") {
            val obj = change.value as? JsonObject ?: return false
            return PersonDto.from(obj).self
        }
        return false
    }

    private suspend fun applyChange(change: SyncChange) {
        when (change.type) {
            "ADD" -> applyAdd(change)
            "UPDATE" -> applyUpdate(change)
            "REMOVE" -> applyRemove(change)
            else -> throw IllegalStateException(
                "Unsupported sync change type=${change.type} version=${change.version}",
            )
        }
    }

    private suspend fun applyAdd(change: SyncChange) {
        when (change.objectName) {
            "Person" -> {
                val obj = change.value as? JsonObject
                    ?: throw IllegalStateException("Person ADD value 非对象")
                upsertPerson(PersonDto.from(obj))
            }
            "Collection" -> {
                val obj = change.value as? JsonObject
                    ?: throw IllegalStateException("Collection ADD value 非对象")
                upsertCollection(CollectionDto.from(obj))
            }
            "Tag" -> {
                val obj = change.value as? JsonObject
                    ?: throw IllegalStateException("Tag ADD value 非对象")
                upsertTag(TagDto.from(obj))
            }
            in NON_LOCAL_OBJECT_NAMES -> {
                BadgerLog.d(TAG, "applyAdd: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> {
                
                BadgerLog.w(TAG, "applyAdd: 未知 objectName=${change.objectName} version=${change.version}, 跳过")
            }
        }
    }

    private suspend fun upsertPerson(person: PersonDto) {
        if (person.uuid.isBlank()) throw IllegalStateException("Person ADD uuid 缺失")
        
        
        
        if (person.self) rememberSelfPersonId(person.uuid)
        if (isSelfPerson(person.uuid)) {
            applySyncedSelfPerson(person)
            return
        }
        val existing = contactCacheDao.getContactByServerId(person.uuid)
        val contactId: Long
        if (existing != null) {
            val mapped = person.toContactCacheEntity(
                id = existing.id,
                
                avatarPath = existing.avatarPath?.takeIf { existing.avatarUrl == person.profile?.avatarURL },
            )
            contactCacheDao.updateContact(mapped)
            contactId = existing.id
            BadgerLog.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} (update)")
        } else {
            contactId = contactCacheDao.insertContact(
                person.toContactCacheEntity(id = 0L, avatarPath = null),
            )
            BadgerLog.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} (insert id=$contactId)")
        }
        contactPlatformCacheDao.deleteByContact(contactId)
        val rows = person.profile?.toPlatformRows(contactId) ?: emptyList()
        if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
        person.profile?.let { profile ->
            personProfileCacheDao.upsert(profile.toPersonProfileEntity(person.uuid))
            applyBasicInfoFromProfile(contactId, profile)
        }
        contactCacheDao.bumpContact(contactId)
    }

    

    private suspend fun applyBasicInfoFromProfile(contactId: Long, profile: ProfileDto) {
        val fieldDao = db.contactFieldCacheDao()
        val fieldValueDao = db.contactFieldValueCacheDao()
        val fromServer = mapOf(
            "gender" to profile.sex,
            "birthday" to profile.birthday,
            "country" to profile.country,
            "region" to profile.region,
        )
        val fields = fieldDao.getAllFieldsOnce().associateBy { it.fieldKey }
        val existing = fieldValueDao.getFieldValuesByContactOnce(contactId).associateBy { it.fieldId }
        val now = nowMs()
        var written = 0
        var cleared = 0
        fromServer.forEach { (key, serverValue) ->
            val field = fields[key] ?: run {
                BadgerLog.w(TAG, "applyBasicInfoFromProfile: 字段种子缺失 key=$key,跳过")
                return@forEach
            }
            val old = existing[field.id]
            if (serverValue.isNullOrBlank()) {
                if (serverValue == null) return@forEach 
                if (old != null) {
                    fieldValueDao.deleteByContactAndField(contactId, field.id)
                    cleared++
                }
                return@forEach
            }
            if (old?.value == serverValue) return@forEach
            
            
            
            val row = old?.copy(value = serverValue, updateTime = now)
                ?: ContactFieldValueCacheEntity(
                    contactId = contactId,
                    fieldId = field.id,
                    value = serverValue,
                    createTime = now,
                    updateTime = now,
                )
            fieldValueDao.insertOrUpdateFieldValues(listOf(row))
            written++
        }
        if (written > 0 || cleared > 0) {
            BadgerLog.d(TAG, "applyBasicInfoFromProfile: contact=$contactId 写入$written 项,清空$cleared 项")
        }
    }

    private suspend fun upsertCollection(dto: CollectionDto) {
        if (dto.uuid.isBlank()) throw IllegalStateException("Collection ADD uuid 缺失")
        val existing = cardCollectionCacheDao.getCollectionByServerId(dto.uuid)
        val entity = CardCollectionCacheEntity(
            id = existing?.id ?: 0L,
            serverId = dto.uuid,
            name = dto.name,
            description = dto.description,
            backgroundImagePath = existing?.backgroundImagePath,
            dominantColor = existing?.dominantColor,
            coverAvatarUrl = dto.backgroundURL,
            personMembers = listToJson(dto.personMembers),
            createTime = existing?.createTime ?: nowMs(),
            isLocalOnly = false,
        )
        if (existing != null) cardCollectionCacheDao.updateCollection(entity)
        else cardCollectionCacheDao.insertCollection(entity)
        BadgerLog.d(TAG, "upsertCollection: uuid=${dto.uuid.take(8)} name=${dto.name} members=${dto.personMembers.size}")
    }

    private suspend fun upsertTag(dto: TagDto) {
        if (dto.uuid.isBlank()) throw IllegalStateException("Tag ADD uuid 缺失")
        val existing = tagCacheDao.getTagByServerId(dto.uuid)
        val entity = TagCacheEntity(
            id = existing?.id ?: 0L,
            serverId = dto.uuid,
            name = dto.name,
            color = existing?.color ?: 0xFF1976D2L,
            colorHash = dto.colorHash,
            personMembers = listToJson(dto.personMembers),
            pinyinInitial = existing?.pinyinInitial
                ?: if (dto.name.isNotBlank()) PinyinUtils.getContactPinyinInitial(dto.name) else "",
            source = existing?.source ?: "manual",
            showDot = existing?.showDot ?: true,
            createTime = existing?.createTime ?: nowMs(),
            isLocalOnly = false,
        )
        
        val persisted = if (existing != null) {
            tagCacheDao.updateTag(entity)
            entity
        } else {
            val rowId = tagCacheDao.insertTag(entity)
            BadgerLog.d(TAG, "upsertTag: inserted rowId=$rowId uuid=${dto.uuid.take(8)}")
            entity.copy(id = rowId)
        }
        rebuildTagRefs(persisted, dto.personMembers)
        BadgerLog.d(TAG, "upsertTag: uuid=${dto.uuid.take(8)} name=${dto.name} members=${dto.personMembers.size}")
    }

    private suspend fun applyUpdate(change: SyncChange) {
        when (change.objectName) {
            "Person" -> applyPersonUpdate(change, change.fieldName)
            "Collection" -> applyCollectionUpdate(change, change.fieldName)
            "Tag" -> applyTagUpdate(change, change.fieldName)
            in NON_LOCAL_OBJECT_NAMES -> {
                BadgerLog.d(TAG, "applyUpdate: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> {
                
                BadgerLog.w(TAG, "applyUpdate: 未知 objectName=${change.objectName} version=${change.version}, 跳过")
            }
        }
    }

    private suspend fun applyPersonUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Person UPDATE objectId 缺失")
        
        if (isSelfPerson(uuid)) {
            applySelfPersonUpdate(change, fieldName, uuid)
            return
        }
        val local = contactCacheDao.getContactByServerId(uuid) ?: run {
            BadgerLog.w(TAG, "applyPersonUpdate: 本地缺行 uuid=$uuid, 尝试 GET /api/user/persons/$uuid 恢复")
            val remote = serverApi.getPerson(uuid)
            if (remote.uuid != uuid) {
                throw IllegalStateException("Person GET uuid 不匹配 expected=$uuid actual=${remote.uuid}")
            }
            upsertPerson(remote)
            contactCacheDao.getContactByServerId(uuid)
                ?: throw IllegalStateException("Person 回源成功但本地仍不存在 uuid=$uuid")
        }
        
        val syncTime = nowMs()
        when (fieldName) {
            "name" -> {
                val newName = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Person UPDATE name value 缺失 uuid=$uuid")
                contactCacheDao.updateContact(
                    local.copy(
                        name = newName,
                        pinyinInitial = PinyinUtils.getContactPinyinInitial(newName),
                        lastSyncedAt = syncTime,
                    )
                )
            }
            "profile" -> {
                val profileJson = change.value as? JsonObject
                    ?: throw IllegalStateException("Person UPDATE profile value 非对象 uuid=$uuid")
                val profile = ProfileDto.from(profileJson)
                
                
                val (remoteAvatar, localShaped) = ContactMapper.splitRemoteAndLocalAvatar(profile.avatarURL)
                
                
                
                val cachedPath = if (remoteAvatar != null && local.avatarUrl != remoteAvatar) null else local.avatarPath
                contactCacheDao.updateContact(
                    local.copy(
                        avatarUrl = remoteAvatar,
                        avatarPath = localShaped ?: cachedPath,
                        bio = profile.description,
                        platformsJson = profile.toPlatformsJson(),
                        lastSyncedAt = syncTime,
                    )
                )
                contactPlatformCacheDao.deleteByContact(local.id)
                val rows = profile.toPlatformRows(local.id)
                if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
                personProfileCacheDao.upsert(profile.toPersonProfileEntity(uuid))
                applyBasicInfoFromProfile(local.id, profile)
            }
            "updateTime" -> {
                val serverTime = parseServerDateMillis(change.value.contentOrNullSafe())
                if (serverTime <= 0L) {
                    throw IllegalStateException("Person UPDATE updateTime 无法解析 uuid=$uuid")
                }
                contactCacheDao.updateContact(local.copy(updateTime = serverTime, lastSyncedAt = syncTime))
            }
            else -> throw IllegalStateException(
                "Unsupported Person UPDATE fieldName=$fieldName uuid=$uuid version=${change.version}",
            )
        }
        contactCacheDao.bumpContact(local.id)
    }

    

    
    private var cachedSelfPersonId: String? = null

    private fun rememberSelfPersonId(uuid: String) {
        if (cachedSelfPersonId == uuid) return
        cachedSelfPersonId = uuid
        AuthPrefs.writeSelfPersonId(uuid)
        BadgerLog.d(TAG, "selfPersonId 自学习: ${uuid.take(8)}...")
    }

    private suspend fun isSelfPerson(uuid: String): Boolean {
        val known = cachedSelfPersonId
            ?: AuthPrefs.readSelfPersonId()?.also { cachedSelfPersonId = it }
        return known != null && uuid == known
    }

    private suspend fun applySyncedSelfPerson(person: PersonDto) {
        userProfileRepository.applySyncedSelfPerson(person)
        purgeStaleSelfContact(person.uuid)
    }

    private suspend fun applySelfPersonUpdate(change: SyncChange, fieldName: String?, uuid: String) {
        when (fieldName) {
            "name" -> {
                val newName = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Person UPDATE name value 缺失 uuid=$uuid")
                userProfileRepository.applySyncedSelfPerson(PersonDto(uuid = uuid, name = newName))
            }
            "profile" -> {
                val profileJson = change.value as? JsonObject
                    ?: throw IllegalStateException("Person UPDATE profile value 非对象 uuid=$uuid")
                userProfileRepository.applySyncedSelfPerson(
                    PersonDto(uuid = uuid, profile = ProfileDto.from(profileJson)),
                )
            }
            "updateTime" -> {
                
                BadgerLog.d(TAG, "applySelfPersonUpdate: updateTime 事件跳过 uuid=${uuid.take(8)}")
            }
            else -> {
                
                
                BadgerLog.w(TAG, "applySelfPersonUpdate: 未支持的 fieldName=$fieldName uuid=${uuid.take(8)}, 跳过")
            }
        }
        purgeStaleSelfContact(uuid)
    }

    
    private suspend fun purgeStaleSelfContact(selfUuid: String) {
        val stale = contactCacheDao.getContactByServerId(selfUuid) ?: return
        db.contactFieldValueCacheDao().deleteByContact(stale.id)
        contactPlatformCacheDao.deleteByContact(stale.id)
        contactCacheDao.deleteById(stale.id)
        BadgerLog.d(TAG, "purgeStaleSelfContact: 已清除混入联系人列表的自己 id=${stale.id}")
    }

    private suspend fun applyCollectionUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Collection UPDATE objectId 缺失")
        val local = cardCollectionCacheDao.getCollectionByServerId(uuid)
            ?: throw IllegalStateException("Collection UPDATE 本地行缺失 uuid=$uuid")
        val updated = when (fieldName) {
            "name" -> local.copy(
                name = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Collection UPDATE name value 缺失 uuid=$uuid")
            )
            "description" -> local.copy(
                description = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Collection UPDATE description value 缺失 uuid=$uuid")
            )
            "backgroundURL" -> local.copy(
                coverAvatarUrl = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Collection UPDATE backgroundURL value 缺失 uuid=$uuid")
            )
            "personMembers" -> local.copy(personMembers = listToJson(parseUuidList(change.value)))
            else -> throw IllegalStateException(
                "Unsupported Collection UPDATE fieldName=$fieldName uuid=$uuid version=${change.version}",
            )
        }
        cardCollectionCacheDao.updateCollection(updated)
    }

    private suspend fun applyTagUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Tag UPDATE objectId 缺失")
        val local = tagCacheDao.getTagByServerId(uuid)
            ?: throw IllegalStateException("Tag UPDATE 本地行缺失 uuid=$uuid")
        val updated = when (fieldName) {
            "name" -> {
                val newName = change.value.contentOrNullSafe()
                    ?: throw IllegalStateException("Tag UPDATE name value 缺失 uuid=$uuid")
                local.copy(
                    name = newName,
                    pinyinInitial = PinyinUtils.getContactPinyinInitial(newName),
                )
            }
            "colorHash" -> local.copy(colorHash = change.value.contentOrNullSafe())
            "personMembers" -> {
                val members = parseUuidList(change.value)
                local.copy(personMembers = listToJson(members)).also {
                    rebuildTagRefs(it, members)
                }
            }
            else -> throw IllegalStateException(
                "Unsupported Tag UPDATE fieldName=$fieldName uuid=$uuid version=${change.version}",
            )
        }
        tagCacheDao.updateTag(updated)
    }

    private suspend fun applyRemove(change: SyncChange) {
        val uuid = change.objectId ?: throw IllegalStateException("REMOVE objectId 缺失")
        when (change.objectName) {
            "Person" -> {
                val local = contactCacheDao.getContactByServerId(uuid)
                if (local != null) {
                    contactPlatformCacheDao.deleteByContact(local.id)
                    contactTagCacheDao.clearContactTags(local.id)
                    personProfileCacheDao.deleteByServerId(uuid)
                    contactCacheDao.deleteById(local.id)
                    
                    if (!local.avatarPath.isNullOrBlank()) {
                        try {
                            deleteFileQuietly(local.avatarPath)
                            BadgerLog.d(TAG, "applyRemove: Person avatar file removed id=${local.id}")
                        } catch (e: Exception) {
                            BadgerLog.e(TAG, "applyRemove: Person avatar file remove failed id=${local.id}", e)
                        }
                    }
                    BadgerLog.d(TAG, "applyRemove: Person uuid=${uuid.take(8)} 已删本地行 id=${local.id}")
                }
            }
            "Collection" -> cardCollectionCacheDao.deleteCollectionByServerId(uuid)
            "Tag" -> tagCacheDao.deleteTagByServerId(uuid)
            in NON_LOCAL_OBJECT_NAMES -> {
                BadgerLog.d(TAG, "applyRemove: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> {
                
                BadgerLog.w(TAG, "applyRemove: 未知 objectName=${change.objectName} version=${change.version}, 跳过")
            }
        }
    }

    private suspend fun rebuildTagRefs(tag: TagCacheEntity, members: List<String>) {
        contactTagCacheDao.clearByTag(tag.id)
        if (members.isEmpty()) return
        val contacts = contactCacheDao.getContactsByServerIds(members)
        if (contacts.isEmpty()) return
        val now = nowMs()
        contactTagCacheDao.insertCrossRefs(
            contacts.map { contact ->
                ContactTagCacheEntity(
                    contactId = contact.id,
                    tagId = tag.id,
                    source = "manual",
                    confidence = 1.0f,
                    createTime = now,
                )
            }
        )
    }

    private fun listToJson(list: List<String>): String =
        JsonArray(list.map { JsonPrimitive(it) }).toString()

    
    private fun kotlinx.serialization.json.JsonElement?.contentOrNullSafe(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun parseUuidList(value: kotlinx.serialization.json.JsonElement?): List<String> {
        if (value == null || value is JsonNull) return emptyList()
        if (value is JsonArray) {
            return value.mapNotNull { element ->
                (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            }
        }
        if (value is JsonObject) {
            val nested = value["value"]
            if (nested is JsonArray) {
                return nested.mapNotNull { element ->
                    (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                }
            }
        }
        throw IllegalStateException("UUID 列表 value 格式非法: ${value.toString().take(LOG_VALUE_LIMIT)}")
    }

    
    private sealed interface OpOutcome {
        data object Success : OpOutcome
        data class Failed(val error: Throwable) : OpOutcome
        data object BlockedOnCreate : OpOutcome
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val MAX_PULL_ROUNDS = 50
        const val LOG_VALUE_LIMIT = 200
        const val HTTP_BAD_REQUEST = 400
        
        
        
        val NON_LOCAL_OBJECT_NAMES = setOf("Device", "UserSettings", "User", "Notification")
    }
}

private fun OutboxOpType.pushPriority(): Int = when (this) {
    OutboxOpType.CREATE -> 0
    OutboxOpType.PATCH -> 1
    OutboxOpType.MEMBER_ADD, OutboxOpType.MEMBER_REMOVE -> 2
    OutboxOpType.DELETE -> 3
}

data class SyncOnceResult(
    
    val pushedOps: Int,
    val pull: SyncPullResult,
)

data class PushOutcome(
    val pushedOps: Int,
    val failedOps: Int,
)

sealed interface SyncPullResult {
    data class Done(val applied: Int, val cursor: Long) : SyncPullResult {
        override fun toString(): String = "SyncPullResult(applied=$applied, cursor=$cursor)"
    }

    data class Failed(val applied: Int, val cursor: Long) : SyncPullResult

    data object Skipped : SyncPullResult
}
