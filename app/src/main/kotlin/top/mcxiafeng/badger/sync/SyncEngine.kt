package top.mcxiafeng.badger.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.cache.dao.CardCollectionCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactPlatformCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.PersonProfileCacheDao
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.repository.CommitResult
import top.mcxiafeng.badger.data.repository.ContactMapper.buildProfileDto
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPersonProfileEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformRows
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformsJson
import top.mcxiafeng.badger.network.ApiException
import top.mcxiafeng.badger.network.CollectionDto
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.TagDto
import top.mcxiafeng.badger.network.parseServerDateMillis
import top.mcxiafeng.badger.utils.Methods
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 双向同步引擎（规格 §3.3/§3.4）：替代原 `SyncRepository`（单向 pull）。
 *
 * - [pushOnce]：消费通用 Outbox，按 **CREATE → PATCH → MEMBER_* → DELETE** 优先级重放
 *   （同优先级保持 createdAt FIFO）。CREATE 走 [createOnPush]（幂等键 + uuid 兑现 + 400 降级），
 *   其余经 `ServerApi.replayOutboxOp` 直发。
 * - [pullOnce]：`GET /api/user/sync?since=` 增量重放落 Room（原 `SyncRepository.doPull` 原样搬运）。
 * - [syncOnce]：**先 push 再 pull**——否则 pull 到的 ADD 可能与本地 PendingCreate 撞车
 *   （同名不同 uuid）；push 成功后本地已 Synced，pull 的 ADD 按 serverId upsert 自然幂等。
 * - [backfillLocalOnlyCreates]：每次 sync 前扫描存量未上云行补建 CREATE（T16c 一次性回填，
 *   幂等，靠 outbox mergeKey 去重）。
 *
 * 并发模型：所有入口共享 [syncMutex]，WorkManager（OutboxWorker）与手动「立即同步」
 * 不会并发重放同一队列。
 *
 * 结局三态（§3.8）：成功 → markSuccess 出队；失败/未知（超时、断连）→ recordFailure 记账 +
 * 退避保留 PENDING。**没有 FAILED_PERMANENT**——行在成功前不删。
 */
class SyncEngine(
    private val serverApi: ServerApi,
    private val outboxStore: OutboxStore,
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val tagCacheDao: TagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val personProfileCacheDao: PersonProfileCacheDao,
) {

    private val syncMutex = Mutex()
    private val started = AtomicBoolean(false)

    // ============ 入口 ============

    /** 完整一轮同步：回填 CREATE → push → pull。「立即同步」入口。 */
    suspend fun syncOnce(): SyncOnceResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            backfillLocalOnlyCreates()
            val push = pushLocked(includeBackoff = true)
            val pull = doPull()
            SyncOnceResult(pushedOps = push.pushedOps, pull = pull)
        }
    }

    /** [syncOnce] 的幂等变体：已在同步中则跳过。启动 / 引导期 bootstrap 用。 */
    suspend fun syncOnceIfIdle(): SyncOnceResult = withContext(Dispatchers.IO) {
        if (!started.compareAndSet(false, true)) {
            Log.d(TAG, "syncOnceIfIdle: 已在同步中,跳过")
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
            started.set(false)
        }
    }

    /**
     * 只推不拉：消费 Outbox。WorkManager（OutboxWorker）入口。
     *
     * [includeBackoff] 见 [OutboxStore.getReady]：手动同步传 true（立即重试退避行），
     * Worker 触发传 false（尊重退避）。
     */
    suspend fun pushOnce(includeBackoff: Boolean = false): PushOutcome = withContext(Dispatchers.IO) {
        syncMutex.withLock { pushLocked(includeBackoff) }
    }

    /** 只拉不推：增量 pull。 */
    suspend fun pullOnce(): SyncPullResult = withContext(Dispatchers.IO) {
        syncMutex.withLock { doPull() }
    }

    // ============ PushLoop（T16a）============

    private suspend fun pushLocked(includeBackoff: Boolean = false): PushOutcome {
        var pushedOps = 0
        var failedOps = 0
        while (true) {
            val ready = outboxStore.getReady(includeBackoff = includeBackoff)
            if (ready.isEmpty()) break
            var progressed = false
            // CREATE 优先（PATCH/MEMBER 依赖创建后的服务端 uuid），DELETE 最后
            for (op in ready.sortedBy { it.op.pushPriority() }) {
                val outcome = replayOp(op)
                when (outcome) {
                    is OpOutcome.Success -> {
                        outboxStore.markSuccess(op.id)
                        pushedOps++
                        progressed = true
                    }
                    is OpOutcome.Failed -> {
                        // 失败与未知结局同路径：记账 + 退避，保留 PENDING（§3.8 禁止 FAILED_PERMANENT）
                        outboxStore.recordFailure(op.id, outcome.error)
                        failedOps++
                    }
                    OpOutcome.BlockedOnCreate -> {
                        // 等同实体的 CREATE 先兑现；不记 attempts（不是失败，是顺序未到）
                    }
                }
                if (outcome is OpOutcome.Success && op.op == OutboxOpType.CREATE) {
                    // CREATE 兑现会回填同实体其它行的 remoteId / MEMBER payload 的 personUuid，
                    // 内存批次还是旧值 → 立即重取，后续行必须按新 uuid 重放
                    break
                }
                if (outcome is OpOutcome.Failed) break // 已退避，本 pass 结束等下一轮
            }
            if (failedOps > 0) break
            if (!progressed) break // 全部 Blocked：等 CREATE 成功后的下一轮，防自旋
        }
        if (pushedOps > 0 || failedOps > 0) {
            Log.d(TAG, "pushOnce: pushed=$pushedOps failed=$failedOps")
        }
        return PushOutcome(pushedOps = pushedOps, failedOps = failedOps)
    }

    private suspend fun replayOp(op: OutboxOp): OpOutcome {
        if (op.op == OutboxOpType.CREATE) {
            return when (val result = createOnPush(op)) {
                // NotFound：本地行已消失（如删除竞态），op 无意义，出队
                CommitResult.SentSuccess, is CommitResult.Written, CommitResult.NotFound -> OpOutcome.Success
                is CommitResult.SentFailed -> OpOutcome.Failed(IllegalStateException(result.reason))
            }
        }
        val remoteId = resolveRemoteId(op) ?: return OpOutcome.BlockedOnCreate
        return try {
            Log.d(
                TAG,
                "replay: id=${op.id} kind=${op.entityKind} op=${op.op} localId=${op.localId} " +
                    "remote=${remoteId.take(8)} attempts=${op.attempts}",
            )
            serverApi.replayOutboxOp(op.copy(remoteId = remoteId))
            OpOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "replay: id=${op.id} kind=${op.entityKind} op=${op.op} 失败", e)
            OpOutcome.Failed(e)
        }
    }

    /**
     * 非 CREATE op 的 remoteId 解析：identity 已 Synced → 用 DB 当前 serverId（自愈任何漏回填）；
     * PendingCreate → PATCH/MEMBER 等创建兑现后再重放（DELETE 除外：DELETE 用 clientUuid 也幂等，
     * 404 = 从未创建，200 = 清掉未知结局的幽灵行）；行已消失 → 按行自带 remoteId 兜底重放。
     */
    private suspend fun resolveRemoteId(op: OutboxOp): String? {
        val identity = loadIdentity(op.entityKind, op.localId) ?: return op.remoteId
        return when {
            identity is RemoteIdentity.Synced -> identity.serverId
            identity is RemoteIdentity.PendingCreate && op.op == OutboxOpType.DELETE -> op.remoteId
            identity is RemoteIdentity.PendingCreate -> null
            else -> op.remoteId
        }
    }

    // ============ CreateOnPush（T14，规格 §3.3 选项 C）============

    /**
     * 统一 create-on-push：Person / Tag / Collection 三种实体共用。
     *
     * 幂等键（§3.8）：`clientUuid` 首次创建时生成并落盘，重试**复用**、禁止重新生成；
     * Unidentified（存量迁移行）在首次重放时现场生成并落盘到 `serverId + isLocalOnly=true`。
     *
     * 服务端契约缺口（选项 C）：当前 Tag/Collection POST 可能不认识 `uuid` 字段——
     * 400 时降级去掉 uuid 再 POST 一次（每次重放**至多一次**），并打 error 日志。
     *
     * [unsafe-to-retry] Tag/Collection CREATE 在服务端兑现 uuid（ticket A）前是 unsafe-to-retry：
     * 未知结局（超时/断连，POST 可能已生效）后重试可能产生服务端重复行，靠 pull 收敛兜底。
     * PUT / DELETE 天然幂等不受影响。
     *
     * 请求体按 **DB 当前状态** 构建（不是 op.payload——那是入队时的诊断快照）；
     * 入队后的增量编辑由后续 PATCH 行覆盖。
     *
     * 结果复用 [CommitResult]（One-Version Rule，§3.8）。
     */
    internal suspend fun createOnPush(op: OutboxOp): CommitResult = try {
        when (op.entityKind) {
            EntityKind.PERSON -> createOnPushPerson(op)
            EntityKind.TAG -> createOnPushTag(op)
            EntityKind.COLLECTION -> createOnPushCollection(op)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 未知结局与失败同路径：保留 PendingCreate + attempts 退避；绝不标记永久失败
        Log.w(TAG, "createOnPush: kind=${op.entityKind} localId=${op.localId} 失败,保留 PendingCreate", e)
        CommitResult.SentFailed(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun createOnPushPerson(op: OutboxOp): CommitResult {
        val contact = contactCacheDao.getContactById(op.localId) ?: return CommitResult.NotFound
        val identity = contact.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            Log.d(TAG, "createOnPushPerson: id=${contact.id} 已 Synced,跳过 POST")
            return CommitResult.SentSuccess
        }
        if (identity is RemoteIdentity.Unidentified) {
            contactCacheDao.updateContact(contact.copy(serverId = clientUuid, isLocalOnly = true))
        }
        val platforms = contactPlatformCacheDao.getPlatformsByContact(contact.id)
        val serverUuid = serverApi.createPerson(
            contact.name,
            buildProfileDto(contact, platforms),
            clientUuid,
        )
        contactCacheDao.updateContact(contact.copy(serverId = serverUuid, isLocalOnly = false))
        outboxStore.backfillAfterCreate(EntityKind.PERSON, contact.id, clientUuid, serverUuid)
        Log.d(TAG, "createOnPushPerson: id=${contact.id} uuid=${serverUuid.take(8)} name=${contact.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createOnPushTag(op: OutboxOp): CommitResult {
        val tag = tagCacheDao.getTagById(op.localId) ?: return CommitResult.NotFound
        val identity = tag.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            Log.d(TAG, "createOnPushTag: id=${tag.id} 已 Synced,跳过 POST")
            return CommitResult.SentSuccess
        }
        if (identity is RemoteIdentity.Unidentified) {
            tagCacheDao.updateTag(tag.copy(serverId = clientUuid, isLocalOnly = true))
        }
        val serverUuid = createTagWithUuidFallback(tag, clientUuid)
        tagCacheDao.updateTag(tag.copy(serverId = serverUuid, isLocalOnly = false))
        outboxStore.backfillAfterCreate(EntityKind.TAG, tag.id, clientUuid, serverUuid)
        Log.d(TAG, "createOnPushTag: id=${tag.id} uuid=${serverUuid.take(8)} name=${tag.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createOnPushCollection(op: OutboxOp): CommitResult {
        val collection = cardCollectionCacheDao.getCollectionById(op.localId) ?: return CommitResult.NotFound
        val identity = collection.identity()
        val clientUuid = resolveCreateUuid(identity) ?: run {
            Log.d(TAG, "createOnPushCollection: id=${collection.id} 已 Synced,跳过 POST")
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
            // [选项 C 降级] 服务端不认识 uuid 字段 → 去 uuid 再 POST 一次（每次重放至多一次）
            Log.e(TAG, "createOnPushCollection: 服务端 400 拒收 uuid,降级去 uuid 重试 name=${collection.name}", e)
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
        Log.d(TAG, "createOnPushCollection: id=${collection.id} uuid=${serverUuid.take(8)} name=${collection.name}")
        return CommitResult.SentSuccess
    }

    private suspend fun createTagWithUuidFallback(tag: TagCacheEntity, clientUuid: String): String = try {
        serverApi.createTag(tag.name, tag.colorHash, personMembers = null, uuid = clientUuid)
    } catch (e: ApiException) {
        if (e.status != HTTP_BAD_REQUEST) throw e
        Log.e(TAG, "createOnPushTag: 服务端 400 拒收 uuid,降级去 uuid 重试 name=${tag.name}", e)
        serverApi.createTag(tag.name, tag.colorHash, personMembers = null, uuid = null)
    }

    /**
     * CREATE 幂等键解析（§3.3）：已同步 → null（免 POST）；PendingCreate → 复用持久化
     * clientUuid；Unidentified → 现场生成（调用方负责把返回值落盘到 serverId + isLocalOnly=true）。
     */
    private fun resolveCreateUuid(identity: RemoteIdentity): String? = when (identity) {
        is RemoteIdentity.Synced -> null
        is RemoteIdentity.PendingCreate -> identity.clientUuid
        is RemoteIdentity.Unidentified -> UUID.randomUUID().toString()
    }

    private suspend fun loadIdentity(kind: EntityKind, localId: Long): RemoteIdentity? = when (kind) {
        EntityKind.PERSON -> contactCacheDao.getContactById(localId)?.identity()
        EntityKind.TAG -> tagCacheDao.getTagById(localId)?.identity()
        EntityKind.COLLECTION -> cardCollectionCacheDao.getCollectionById(localId)?.identity()
    }

    // ============ 存量回填（T16c）============

    /**
     * 一次性回填（不是迁移 SQL）：扫描存量未上云行补建 CREATE op。幂等——
     * 已有 CREATE 的行被 outbox mergeKey 忽略（IgnoredDuplicateCreate）。
     *
     * 扫描谓词：Person 按 `isLocalOnly=1`；Tag/Collection 额外包含 `serverId IS NULL`
     * （历史版本创建失败遗留的 Unidentified 行，同样从未到达服务端）。
     * Tag/Collection 的 CREATE payload 只带基础字段——成员关系由 MEMBER_* 行承载，
     * 不随 CREATE 传（服务端成员子接口独立校验归属）。
     */
    private suspend fun backfillLocalOnlyCreates(): Int {
        var created = 0
        contactCacheDao.getLocalOnlyContactsOnce().forEach { contact ->
            val result = outboxStore.enqueue(
                EntityKind.PERSON, contact.id, contact.serverId, OutboxOpType.CREATE,
                com.google.gson.JsonObject().apply {
                    addProperty("name", contact.name)
                    add("profile", buildProfileDto(contact, emptyList()).toJsonObject())
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        tagCacheDao.getNeverSyncedTagsOnce().forEach { tag ->
            val result = outboxStore.enqueue(
                EntityKind.TAG, tag.id, tag.serverId, OutboxOpType.CREATE,
                com.google.gson.JsonObject().apply {
                    addProperty("name", tag.name)
                    tag.colorHash?.takeIf { it.isNotBlank() }?.let { addProperty("colorHash", it) }
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        cardCollectionCacheDao.getNeverSyncedCollectionsOnce().forEach { collection ->
            val result = outboxStore.enqueue(
                EntityKind.COLLECTION, collection.id, collection.serverId, OutboxOpType.CREATE,
                com.google.gson.JsonObject().apply {
                    addProperty("name", collection.name)
                    collection.description?.let { addProperty("description", it) }
                    collection.coverAvatarUrl?.let { addProperty("backgroundURL", it) }
                },
            )
            if (result is OutboxEnqueueResult.Created) created++
        }
        if (created > 0) Log.d(TAG, "backfillLocalOnlyCreates: 补建 $created 条 CREATE op")
        return created
    }

    // ============ PullLoop（T16b，自 SyncRepository 原样搬运）============

    private suspend fun doPull(): SyncPullResult {
        var cursor = syncCursorDao.getLastVersion() ?: 0L
        var applied = 0
        var rounds = 0
        var hasMore = true

        while (hasMore && rounds < MAX_PULL_ROUNDS) {
            rounds++
            val page = try {
                serverApi.syncSince(cursor)
            } catch (e: Exception) {
                Log.w(TAG, "doPull: syncSince($cursor) 失败 rounds=$rounds", e)
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            if (page.version < cursor) {
                Log.e(TAG, "doPull: 服务端游标回退 $cursor -> ${page.version}")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }
            if (page.changes.isEmpty()) {
                if (page.hasMore) {
                    Log.e(TAG, "doPull: 空批次却 hasMore=true, cursor=$cursor version=${page.version}")
                    return SyncPullResult.Failed(applied = applied, cursor = cursor)
                }
                if (page.version != cursor) {
                    Log.e(TAG, "doPull: 空批次 version 非当前游标 $cursor -> ${page.version}, 拒绝跳跃")
                    return SyncPullResult.Failed(applied = applied, cursor = cursor)
                }
                hasMore = false
                break
            }
            if (page.version == cursor) {
                Log.e(TAG, "doPull: 有变更但 version 未前进 cursor=$cursor")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            if (!applyChanges(page.changes)) {
                Log.e(TAG, "doPull: 批次应用失败,游标保持 $cursor,下轮重放 changes=${page.changes.size}")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }

            applied += page.changes.size
            cursor = page.version
            syncCursorDao.upsert(
                SyncCursorEntity(
                    lastVersion = cursor,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            hasMore = page.hasMore
            Log.d(TAG, "doPull: 批次完成 changes=${page.changes.size} cursor=$cursor hasMore=$hasMore")
        }

        if (hasMore) {
            Log.e(TAG, "doPull: 达到最大拉取轮数 $MAX_PULL_ROUNDS, 当前 cursor=$cursor, 仍有 hasMore=true")
            return SyncPullResult.Failed(applied = applied, cursor = cursor)
        }

        Log.d(TAG, "doPull: 完成 applied=$applied cursor=$cursor rounds=$rounds")
        return SyncPullResult.Done(applied = applied, cursor = cursor)
    }

    /** 应用一批 change；任一条失败 → 游标保持不动并在下轮重放。 */
    private suspend fun applyChanges(changes: List<SyncChange>): Boolean {
        for (change in changes) {
            try {
                applyChange(change)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "applyChanges: version=${change.version} type=${change.type} object=${change.objectName} failed",
                    e,
                )
                return false
            }
        }
        return true
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
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw IllegalStateException("Person ADD value 非对象")
                upsertPerson(PersonDto.from(obj))
            }
            "Collection" -> {
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw IllegalStateException("Collection ADD value 非对象")
                upsertCollection(CollectionDto.from(obj))
            }
            "Tag" -> {
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw IllegalStateException("Tag ADD value 非对象")
                upsertTag(TagDto.from(obj))
            }
            in NON_LOCAL_OBJECT_NAMES -> {
                Log.d(TAG, "applyAdd: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> throw IllegalStateException(
                "Unsupported sync objectName=${change.objectName} for ADD version=${change.version}",
            )
        }
    }

    private suspend fun upsertPerson(person: PersonDto) {
        if (person.uuid.isBlank()) throw IllegalStateException("Person ADD uuid 缺失")
        val existing = contactCacheDao.getContactByServerId(person.uuid)
        val contactId: Long
        if (existing != null) {
            val mapped = person.toContactCacheEntity(id = existing.id, avatarPath = existing.avatarPath)
            contactCacheDao.updateContact(mapped)
            contactId = existing.id
            Log.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} (update)")
        } else {
            contactId = contactCacheDao.insertContact(
                person.toContactCacheEntity(id = 0L, avatarPath = null),
            )
            Log.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} (insert id=$contactId)")
        }
        contactPlatformCacheDao.deleteByContact(contactId)
        val rows = person.profile?.toPlatformRows(contactId) ?: emptyList()
        if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
        person.profile?.let { profile ->
            personProfileCacheDao.upsert(profile.toPersonProfileEntity(person.uuid))
        }
        contactCacheDao.bumpContact(contactId)
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
            createTime = existing?.createTime ?: System.currentTimeMillis(),
            isLocalOnly = false,
        )
        if (existing != null) cardCollectionCacheDao.updateCollection(entity)
        else cardCollectionCacheDao.insertCollection(entity)
        Log.d(TAG, "upsertCollection: uuid=${dto.uuid.take(8)} name=${dto.name} members=${dto.personMembers.size}")
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
            createTime = existing?.createTime ?: System.currentTimeMillis(),
            isLocalOnly = false,
        )
        // [F1] 新标签 insertTag 的返回 rowId 必须回填 entity，否则 rebuildTagRefs 全写到 tagId=0
        val persisted = if (existing != null) {
            tagCacheDao.updateTag(entity)
            entity
        } else {
            val rowId = tagCacheDao.insertTag(entity)
            Log.d(TAG, "upsertTag: inserted rowId=$rowId uuid=${dto.uuid.take(8)}")
            entity.copy(id = rowId)
        }
        rebuildTagRefs(persisted, dto.personMembers)
        Log.d(TAG, "upsertTag: uuid=${dto.uuid.take(8)} name=${dto.name} members=${dto.personMembers.size}")
    }

    private suspend fun applyUpdate(change: SyncChange) {
        when (change.objectName) {
            "Person" -> applyPersonUpdate(change, change.fieldName)
            "Collection" -> applyCollectionUpdate(change, change.fieldName)
            "Tag" -> applyTagUpdate(change, change.fieldName)
            in NON_LOCAL_OBJECT_NAMES -> {
                Log.d(TAG, "applyUpdate: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> throw IllegalStateException(
                "Unsupported sync objectName=${change.objectName} for UPDATE version=${change.version}",
            )
        }
    }

    private suspend fun applyPersonUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Person UPDATE objectId 缺失")
        val local = contactCacheDao.getContactByServerId(uuid) ?: run {
            Log.w(TAG, "applyPersonUpdate: 本地缺行 uuid=$uuid, 尝试 GET /api/user/persons/$uuid 恢复")
            val remote = serverApi.getPerson(uuid)
            if (remote.uuid != uuid) {
                throw IllegalStateException("Person GET uuid 不匹配 expected=$uuid actual=${remote.uuid}")
            }
            upsertPerson(remote)
            contactCacheDao.getContactByServerId(uuid)
                ?: throw IllegalStateException("Person 回源成功但本地仍不存在 uuid=$uuid")
        }
        when (fieldName) {
            "name" -> {
                val newName = change.value?.takeIf { !it.isJsonNull }?.asString
                    ?: throw IllegalStateException("Person UPDATE name value 缺失 uuid=$uuid")
                contactCacheDao.updateContact(
                    local.copy(
                        name = newName,
                        pinyinInitial = PinyinUtils.getContactPinyinInitial(newName),
                    )
                )
            }
            "profile" -> {
                val profileJson = change.value?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw IllegalStateException("Person UPDATE profile value 非对象 uuid=$uuid")
                val profile = ProfileDto.from(profileJson)
                contactCacheDao.updateContact(
                    local.copy(
                        avatarUrl = profile.avatarURL,
                        bio = profile.description,
                        platformsJson = profile.toPlatformsJson(),
                    )
                )
                contactPlatformCacheDao.deleteByContact(local.id)
                val rows = profile.toPlatformRows(local.id)
                if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
                personProfileCacheDao.upsert(profile.toPersonProfileEntity(uuid))
            }
            "updateTime" -> {
                val serverTime = parseServerDateMillis(
                    change.value?.takeIf { !it.isJsonNull }?.asString,
                )
                if (serverTime <= 0L) {
                    throw IllegalStateException("Person UPDATE updateTime 无法解析 uuid=$uuid")
                }
                contactCacheDao.updateContact(local.copy(updateTime = serverTime))
            }
            else -> throw IllegalStateException(
                "Unsupported Person UPDATE fieldName=$fieldName uuid=$uuid version=${change.version}",
            )
        }
        contactCacheDao.bumpContact(local.id)
    }

    private suspend fun applyCollectionUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Collection UPDATE objectId 缺失")
        val local = cardCollectionCacheDao.getCollectionByServerId(uuid)
            ?: throw IllegalStateException("Collection UPDATE 本地行缺失 uuid=$uuid")
        val updated = when (fieldName) {
            "name" -> local.copy(
                name = change.value?.takeIf { !it.isJsonNull }?.asString
                    ?: throw IllegalStateException("Collection UPDATE name value 缺失 uuid=$uuid")
            )
            "description" -> local.copy(
                description = change.value?.takeIf { !it.isJsonNull }?.asString
                    ?: throw IllegalStateException("Collection UPDATE description value 缺失 uuid=$uuid")
            )
            "backgroundURL" -> local.copy(
                coverAvatarUrl = change.value?.takeIf { !it.isJsonNull }?.asString
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
                val newName = change.value?.takeIf { !it.isJsonNull }?.asString
                    ?: throw IllegalStateException("Tag UPDATE name value 缺失 uuid=$uuid")
                local.copy(
                    name = newName,
                    pinyinInitial = PinyinUtils.getContactPinyinInitial(newName),
                )
            }
            "colorHash" -> local.copy(colorHash = change.value?.takeIf { !it.isJsonNull }?.asString)
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
                    // [T09] sync REMOVE 也要回收本地头像文件（对齐 hardDeleteContact）
                    if (!local.avatarPath.isNullOrBlank()) {
                        try {
                            Methods.deleteAvatarFile(local.avatarPath)
                            Log.d(TAG, "applyRemove: Person avatar file removed id=${local.id}")
                        } catch (e: Exception) {
                            Log.e(TAG, "applyRemove: Person avatar file remove failed id=${local.id}", e)
                        }
                    }
                    Log.d(TAG, "applyRemove: Person uuid=${uuid.take(8)} 已删本地行 id=${local.id}")
                }
            }
            "Collection" -> cardCollectionCacheDao.deleteCollectionByServerId(uuid)
            "Tag" -> tagCacheDao.deleteTagByServerId(uuid)
            in NON_LOCAL_OBJECT_NAMES -> {
                Log.d(TAG, "applyRemove: objectName=${change.objectName} 无本地投影,明确忽略")
            }
            else -> throw IllegalStateException(
                "Unsupported sync objectName=${change.objectName} for REMOVE version=${change.version}",
            )
        }
    }

    private suspend fun rebuildTagRefs(tag: TagCacheEntity, members: List<String>) {
        contactTagCacheDao.clearByTag(tag.id)
        if (members.isEmpty()) return
        val contacts = contactCacheDao.getContactsByServerIds(members)
        if (contacts.isEmpty()) return
        val now = System.currentTimeMillis()
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

    private fun listToJson(list: List<String>): String {
        val arr = com.google.gson.JsonArray()
        list.forEach { arr.add(it) }
        return arr.toString()
    }

    private fun parseUuidList(value: com.google.gson.JsonElement?): List<String> {
        if (value == null || value.isJsonNull) return emptyList()
        if (value.isJsonArray) {
            return value.asJsonArray.mapNotNull { element ->
                if (element.isJsonNull) null else element.asString
            }
        }
        if (value.isJsonObject) {
            val nested = value.asJsonObject.get("value")
            if (nested != null && nested.isJsonArray) {
                return nested.asJsonArray.mapNotNull { element ->
                    element.takeIf { !it.isJsonNull }?.asString
                }
            }
        }
        throw IllegalStateException("UUID 列表 value 格式非法: ${value.toString().take(LOG_VALUE_LIMIT)}")
    }

    /** 单条 op 的重放结局（SyncEngine 内部分发用；仓库层提交结果统一是 [CommitResult]）。 */
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
        val NON_LOCAL_OBJECT_NAMES = setOf("Device", "UserSettings")
    }
}

/** push 重放优先级：CREATE → PATCH → MEMBER_* → DELETE（同优先级保持 createdAt FIFO）。 */
private fun OutboxOpType.pushPriority(): Int = when (this) {
    OutboxOpType.CREATE -> 0
    OutboxOpType.PATCH -> 1
    OutboxOpType.MEMBER_ADD, OutboxOpType.MEMBER_REMOVE -> 2
    OutboxOpType.DELETE -> 3
}

/** 一轮完整同步的结果（引擎级汇报；仓库层提交结果统一是 [CommitResult]）。 */
data class SyncOnceResult(
    /** 本轮成功推送到服务端的 outbox op 数。 */
    val pushedOps: Int,
    val pull: SyncPullResult,
)

/** 一次 pushOnce 的结果。 */
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
