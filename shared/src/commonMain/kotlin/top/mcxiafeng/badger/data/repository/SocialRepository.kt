package top.mcxiafeng.badger.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Person
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.network.core.CollectionApi
import top.mcxiafeng.badger.network.core.PersonApi
import top.mcxiafeng.badger.network.core.TagsApi
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncAtomReplayer
import top.mcxiafeng.badger.sync.SyncScope
import top.mcxiafeng.badger.sync.SyncType
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.uuid.Uuid

/** 单类实体的拉取步骤结果。 */
sealed interface PullStep {
    data class Updated(val count: Int) : PullStep
    data object SkippedByPending : PullStep
    data object Failed : PullStep
}

/** refreshAll 汇总报告。 */
data class PullReport(
    val persons: PullStep,
    val collections: PullStep,
    val tags: PullStep,
) {
    fun summary(): String =
        "联系人:${persons.summaryText()} 名片夹:${collections.summaryText()} 标签:${tags.summaryText()}"

    private fun PullStep.summaryText(): String = when (this) {
        is PullStep.Updated -> "更新${count}条"
        PullStep.SkippedByPending -> "等待同步"
        PullStep.Failed -> "拉取失败"
    }
}

/**
 * 本地优先 Repository：读 = Room Flow（UI 订阅自动刷新）；
 * 拉 = 服务端覆盖本地（规则1/3），有 pending 写操作则跳过（规则2）；
 * 写 = 先记 pending（SyncAtom），再乐观更新本地，并自动触发 push 重放。
 *
 * pushPending 用 companion 级 Mutex 串行化：多个实例（VM 每次新建）共享同一批 DB，
 * 防止并发重放同一批 atom。
 */
class SocialRepository(
    private val cache: CacheDatabase,
    private val system: SystemDatabase,
    private val externalScope: CoroutineScope = SyncScope.scope,
    private val autoPush: Boolean = true,
) {

    private val replayer = SyncAtomReplayer(cache, system)

    // ---------- 读：本地优先 ----------

    fun observePersons(): Flow<List<Person>> = cache.personDao().observeAllPersons()

    fun observeCollections(): Flow<List<Collection>> = cache.collectionDao().observeAllCollections()

    fun observeTags(): Flow<List<Tags>> = cache.tagsDao().observeAllTags()

    fun observePendingCount(): Flow<Int> = system.syncAtomDao().observePendingCount()

    // ---------- 拉：规则1/3 服务端覆盖；规则2 pending 门控 ----------

    suspend fun refreshAll(): PullReport {
        val report = PullReport(
            persons = refreshPersons(),
            collections = refreshCollections(),
            tags = refreshTags(),
        )
        BadgerLog.d(TAG, "refreshAll: ${report.summary()}")
        return report
    }

    suspend fun refreshPersons(): PullStep {
        if (hasPending(EntityKind.PERSON)) return PullStep.SkippedByPending
        val remote = PersonApi.getPersons() ?: run {
            BadgerLog.d(TAG, "refreshPersons 失败：API 返回 null")
            return PullStep.Failed
        }
        cache.withTransaction {
            cache.personDao().upsertAll(remote)
            if (remote.isEmpty()) cache.personDao().deleteAllPersons()
            else cache.personDao().deletePersonsNotIn(remote.map { it.uuid })
        }
        return PullStep.Updated(remote.size)
    }

    suspend fun refreshCollections(): PullStep {
        if (hasPending(EntityKind.COLLECTION)) return PullStep.SkippedByPending
        val remote = CollectionApi.getCollections() ?: run {
            BadgerLog.d(TAG, "refreshCollections 失败：API 返回 null")
            return PullStep.Failed
        }
        cache.withTransaction {
            cache.collectionDao().upsertAll(remote)
            if (remote.isEmpty()) cache.collectionDao().deleteAllCollections()
            else cache.collectionDao().deleteCollectionsNotIn(remote.map { it.uuid })
        }
        return PullStep.Updated(remote.size)
    }

    suspend fun refreshTags(): PullStep {
        if (hasPending(EntityKind.TAG)) return PullStep.SkippedByPending
        val remote = TagsApi.getTags() ?: run {
            BadgerLog.d(TAG, "refreshTags 失败：API 返回 null")
            return PullStep.Failed
        }
        cache.withTransaction {
            cache.tagsDao().upsertAll(remote)
            if (remote.isEmpty()) cache.tagsDao().deleteAllTags()
            else cache.tagsDao().deleteTagsNotIn(remote.map { it.uuid })
        }
        return PullStep.Updated(remote.size)
    }

    // ---------- 写：乐观更新 + 记 pending ----------
    // 顺序 = 先落 atom 再动本地缓存：中途崩溃时 atom 已在，push 重放可自愈。

    suspend fun savePerson(person: Person) {
        val syncType = if (cache.personDao().getPerson(person.uuid) != null) SyncType.UPDATE else SyncType.INSERT
        enqueue(EntityKind.PERSON, syncType, Json.encodeToJsonElement(person).jsonObject)
        cache.personDao().upsertPerson(person)
    }

    suspend fun deletePerson(uuid: Uuid) {
        enqueue(EntityKind.PERSON, SyncType.DELETE, uuidPayload(uuid))
        cache.personDao().deletePersonByUuid(uuid)
    }

    suspend fun saveCollection(collection: Collection) {
        val syncType = if (cache.collectionDao().getCollection(collection.uuid) != null) {
            SyncType.UPDATE
        } else {
            SyncType.INSERT
        }
        enqueue(EntityKind.COLLECTION, syncType, Json.encodeToJsonElement(collection).jsonObject)
        cache.collectionDao().upsertCollection(collection)
    }

    suspend fun deleteCollection(uuid: Uuid) {
        enqueue(EntityKind.COLLECTION, SyncType.DELETE, uuidPayload(uuid))
        cache.collectionDao().deleteCollectionByUuid(uuid)
    }

    suspend fun saveTag(tag: Tags) {
        val syncType = if (cache.tagsDao().getTags(tag.uuid) != null) SyncType.UPDATE else SyncType.INSERT
        enqueue(EntityKind.TAG, syncType, Json.encodeToJsonElement(tag).jsonObject)
        cache.tagsDao().upsertTags(tag)
    }

    suspend fun deleteTag(uuid: Uuid) {
        enqueue(EntityKind.TAG, SyncType.DELETE, uuidPayload(uuid))
        cache.tagsDao().deleteTagsByUuid(uuid)
    }

    // ---------- 推：重放 pending 到服务端，成功的类别立即拉取（规则2 闭环） ----------

    suspend fun pushPending(): SyncAtomReplayer.Report = pushMutex.withLock {
        val report = replayer.replayAll()
        BadgerLog.d(TAG, "pushPending: ${report.summary()}")
        report.kinds
            .filter { it.allSucceeded && it.replayed > 0 }
            .forEach { outcome ->
                when (outcome.kind) {
                    EntityKind.PERSON -> refreshPersons()
                    EntityKind.COLLECTION -> refreshCollections()
                    EntityKind.TAG -> refreshTags()
                    EntityKind.PROFILE -> {}
                }
            }
        report
    }

    private fun schedulePush() {
        if (!autoPush) return
        externalScope.launch {
            runCatching { pushPending() }
                .onFailure { BadgerLog.e(TAG, "自动推送失败", it) }
        }
    }

    // ---------- 内部 ----------

    private suspend fun hasPending(kind: EntityKind): Boolean {
        val count = system.syncAtomDao().pendingCount(kind)
        if (count > 0) {
            BadgerLog.d(TAG, "拉取跳过：$kind 有 $count 条 pending")
        }
        return count > 0
    }

    private suspend fun enqueue(kind: EntityKind, syncType: SyncType, payload: JsonObject) {
        val id = system.syncAtomDao().insertAtom(
            SyncAtom(
                entityKind = kind,
                syncType = syncType,
                data = payload,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
        BadgerLog.d(TAG, "pending 记录#$id: $kind/$syncType")
        schedulePush()
    }

    private fun uuidPayload(uuid: Uuid): JsonObject = buildJsonObject { put("uuid", uuid.toString()) }

    companion object {
        private const val TAG = "SocialRepositoryTester"

        /** companion 级：跨实例串行化 pushPending（atom 表全局共享）。 */
        private val pushMutex = Mutex()
    }
}
