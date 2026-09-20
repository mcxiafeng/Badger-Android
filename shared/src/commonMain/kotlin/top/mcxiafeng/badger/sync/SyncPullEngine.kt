package top.mcxiafeng.badger.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.data.repository.PullReport
import top.mcxiafeng.badger.data.repository.SocialRepository
import top.mcxiafeng.badger.data.system.database.SystemDatabase
import top.mcxiafeng.badger.data.system.database.SystemDbHolder
import top.mcxiafeng.badger.data.user.database.CacheDatabase
import top.mcxiafeng.badger.data.user.database.CacheDbHolder
import top.mcxiafeng.badger.data.user.entity.Collection
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.network.core.SyncApi
import top.mcxiafeng.badger.network.core.SyncChange
import top.mcxiafeng.badger.utils.BadgerLog
import kotlin.uuid.Uuid

/**
 * 增量拉取引擎：以 UserSyncState.syncVersion 为游标消费服务端 op-log
 * （GET /api/user/sync?since=游标），重放 Collection/Tag 变更并用
 * advanceSyncCursor 回写游标 + 服务端下发时间。
 *
 * 契约要点：
 * - 游标 0 = 从未同步：先走 [SocialRepository.refreshAll] 全量 bootstrap
 *   （Person 服务端无埋点，只有 list 全量这一条同步通路），再从 0 重放历史；
 * - lastSyncTime 只取本批变更行携带的服务端时钟（createTime），无变更行时保持原值；
 * - 本地有 pending 的类别整类跳过重放、游标照常推进——规则 2 由 push 成功后的
 *   闭环 refresh 兜底收敛，不能让游标停在旧版本空转；
 * - 变更重放失败即停批、游标不动（重放幂等，下轮原批重试），与 SyncAtomReplayer
 *   的保序语义一致；
 * - UPDATE 缺本地行 = 前置 ADD 未生效的分叉：对该类别做一次全量 refresh 收敛
 *   （refresh 本身就是服务端状态覆盖，顺带应用了这条 UPDATE）。
 */
class SyncPullEngine(
    private val cache: CacheDatabase = CacheDbHolder.get(),
    private val system: SystemDatabase = SystemDbHolder.get(),
    private val social: SocialRepository = SocialRepository(cache, system),
) {

    data class Report(
        val bootstrap: PullReport?,
        val batches: Int,
        val cursor: Long,
        val lastSyncTime: Long,
        val gatedKinds: Set<EntityKind>,
        val skippedUnknown: Int,
        val skippedMissing: Int,
        val error: String?,
    ) {
        fun summary(): String = buildString {
            bootstrap?.let { append("全量[$it] ") }
            append("批次=$batches 游标=$cursor 时间=$lastSyncTime")
            if (gatedKinds.isNotEmpty()) append(" 门控=$gatedKinds")
            if (skippedUnknown > 0) append(" 未路由=$skippedUnknown")
            if (skippedMissing > 0) append(" 缺行=$skippedMissing")
            error?.let { append(" 错误=$it") }
        }
    }

    /**
     * 从当前游标拉到服务端最新。未登录（载体无行）返回 null。
     * 中途失败返回已推进部分的报告（游标停在最后成功批次，下次续拉）。
     */
    suspend fun pullAll(): Report? = pullMutex.withLock {
        val state = system.userSyncStateDao().getActiveState()
        if (state == null) {
            BadgerLog.d(TAG, "未登录，跳过增量拉取")
            return null
        }

        var bootstrap: PullReport? = null
        if (state.syncVersion <= 0L) {
            bootstrap = social.refreshAll()
            BadgerLog.d(TAG, "bootstrap 全量: ${bootstrap.summary()}")
        }

        var cursor = state.syncVersion
        var lastSyncTime = state.lastSyncTime
        var batches = 0
        var skippedUnknown = 0
        var skippedMissing = 0
        var lastBatchHasMore = false
        val gatedKinds = mutableSetOf<EntityKind>()
        val refreshedKinds = mutableSetOf<EntityKind>()
        var error: String? = null

        while (error == null && batches < MAX_BATCHES) {
            val batch = SyncApi.pull(since = cursor)
            if (batch == null) {
                error = "拉取批次失败（游标=$cursor）"
                break
            }
            if (batch.changes.isEmpty()) break

            for (kind in EntityKind.entries) {
                if (system.syncAtomDao().pendingCount(kind) > 0) gatedKinds += kind
            }

            for (change in batch.changes) {
                val kind = route(change.objectName)
                if (kind == null) {
                    skippedUnknown++
                    BadgerLog.d(TAG, "未路由变更: ${change.objectName}/${change.type}#${change.objectId}")
                    continue
                }
                if (kind in gatedKinds) {
                    BadgerLog.d(TAG, "pending 门控跳过: $kind/${change.type}#${change.objectId}")
                    continue
                }
                val outcome = try {
                    apply(kind, change)
                } catch (e: Exception) {
                    error = "重放失败: $kind/${change.type}#${change.objectId} — ${e.message}"
                    BadgerLog.e(TAG, "重放失败，停批保游标（cursor=$cursor）", e)
                    break
                }
                if (outcome == ApplyOutcome.MissingLocal) {
                    if (refreshedKinds.add(kind)) refreshKind(kind)
                    skippedMissing++
                }
            }
            if (error != null) break

            val serverTime = batch.changes.last().createTime ?: lastSyncTime
            system.userSyncStateDao().advanceSyncCursor(state.userUuid, batch.version, serverTime)
            cursor = batch.version
            lastSyncTime = serverTime
            batches++
            lastBatchHasMore = batch.hasMore
            if (!batch.hasMore) break
        }
        // 精确判定：最后一批仍 hasMore 却达到上限才是真异常（正常跑满上限收尾不算）
        if (batches >= MAX_BATCHES && lastBatchHasMore && error == null) error = "批次超上限（疑似 hasMore 异常）"

        val report = Report(
            bootstrap = bootstrap,
            batches = batches,
            cursor = cursor,
            lastSyncTime = lastSyncTime,
            gatedKinds = gatedKinds,
            skippedUnknown = skippedUnknown,
            skippedMissing = skippedMissing,
            error = error,
        )
        BadgerLog.d(TAG, "pullAll: ${report.summary()}")
        return report
    }

    /** 路由 objectName → EntityKind；服务端未埋点的类别（Person/Profile/Device…）返回 null。 */
    private fun route(objectName: String): EntityKind? = when (objectName) {
        "Collection" -> EntityKind.COLLECTION
        "Tag" -> EntityKind.TAG
        else -> null
    }

    private suspend fun apply(kind: EntityKind, change: SyncChange): ApplyOutcome {
        val uuid = change.objectId.let { Uuid.parse(it) }
        return when (kind) {
            EntityKind.COLLECTION -> applyCollection(uuid, change)
            EntityKind.TAG -> applyTag(uuid, change)
            else -> error("类别 $kind 不在重放范围")
        }
    }

    private suspend fun applyCollection(uuid: Uuid, change: SyncChange): ApplyOutcome = when (change.type) {
        "REMOVE" -> {
            cache.collectionDao().deleteCollectionByUuid(uuid)
            ApplyOutcome.Applied
        }
        "ADD" -> {
            val entity = Json.decodeFromJsonElement<Collection>(requireSnapshot(change))
            cache.collectionDao().upsertCollection(entity)
            ApplyOutcome.Applied
        }
        "UPDATE" -> {
            val merged = mergeLocal(cache.collectionDao().getCollection(uuid), change)
                ?: return ApplyOutcome.MissingLocal
            cache.collectionDao().upsertCollection(Json.decodeFromJsonElement<Collection>(merged))
            ApplyOutcome.Applied
        }
        else -> error("未知变更类型 ${change.type}")
    }

    private suspend fun applyTag(uuid: Uuid, change: SyncChange): ApplyOutcome = when (change.type) {
        "REMOVE" -> {
            cache.tagsDao().deleteTagsByUuid(uuid)
            ApplyOutcome.Applied
        }
        "ADD" -> {
            val entity = Json.decodeFromJsonElement<Tags>(requireSnapshot(change))
            cache.tagsDao().upsertTags(entity)
            ApplyOutcome.Applied
        }
        "UPDATE" -> {
            val merged = mergeLocal(cache.tagsDao().getTags(uuid), change)
                ?: return ApplyOutcome.MissingLocal
            cache.tagsDao().upsertTags(Json.decodeFromJsonElement<Tags>(merged))
            ApplyOutcome.Applied
        }
        else -> error("未知变更类型 ${change.type}")
    }

    /**
     * ADD 快照重整形：collectionSnapshot/tagSnapshot 的 owner 是 uuid 字符串——
     * Collection 客户端字段名 ownerUuid 改写接入，Tag 客户端无此字段丢弃。
     */
    private fun requireSnapshot(change: SyncChange) = buildJsonObject {
        val raw = Json.parseToJsonElement(requireNotNull(change.value) { "ADD 快照缺失" }).jsonObject
        raw.forEach { (key, element) ->
            when {
                key == "owner" && change.objectName == "Collection" -> put("ownerUuid", element)
                key == "owner" -> {}
                else -> put(key, element)
            }
        }
    }

    /** UPDATE = 本地行 JSON 按服务端 fieldName 合并新值；本地缺行返回 null 交给收敛逻辑。 */
    private fun mergeLocal(local: Collection?, change: SyncChange) = local?.let {
        mergeField(Json.encodeToJsonElement(it).jsonObject, change)
    }

    private fun mergeLocal(local: Tags?, change: SyncChange) = local?.let {
        mergeField(Json.encodeToJsonElement(it).jsonObject, change)
    }

    private fun mergeField(entity: JsonObject, change: SyncChange) = buildJsonObject {
        entity.forEach { (key, element) -> put(key, element) }
        put(
            requireNotNull(change.fieldName) { "UPDATE 缺 fieldName" },
            Json.parseToJsonElement(requireNotNull(change.value) { "UPDATE 缺 value" }),
        )
    }

    private suspend fun refreshKind(kind: EntityKind) {
        val step = when (kind) {
            EntityKind.COLLECTION -> social.refreshCollections()
            EntityKind.TAG -> social.refreshTags()
            else -> return
        }
        BadgerLog.d(TAG, "缺行收敛 refresh $kind: $step")
    }

    private sealed interface ApplyOutcome {
        data object Applied : ApplyOutcome
        data object MissingLocal : ApplyOutcome
    }

    companion object {
        private const val TAG = "SyncPullEngineTester"

        /** 防服务端 hasMore 异常导致死循环的单次拉取批数上限。 */
        private const val MAX_BATCHES = 100

        /** companion 级：引擎可多实例构造，拉取全局串行（游标只有一个）。 */
        private val pullMutex = Mutex()
    }
}
