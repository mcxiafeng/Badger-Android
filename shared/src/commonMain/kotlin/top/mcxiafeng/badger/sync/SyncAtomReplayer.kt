package top.mcxiafeng.badger.sync

import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

/** 单条 atom 重放结果：success + 服务端返回的 uuid（create 类需要回填本地）。 */
private data class AtomOutcome(val success: Boolean, val serverUuid: Uuid?, val error: String?)

class SyncAtomReplayer(
    private val cache: CacheDatabase,
    private val system: SystemDatabase,
) {

    /** 单类别重放结果。failed=true 表示该类别因失败中途停止（保序），仍有 pending。 */
    data class KindOutcome(
        val kind: EntityKind,
        val replayed: Int,
        val quarantined: Int,
        val failed: Boolean,
        val lastError: String?,
    ) {
        val allSucceeded: Boolean get() = !failed
    }

    data class Report(val kinds: List<KindOutcome>) {
        fun outcome(kind: EntityKind): KindOutcome? = kinds.firstOrNull { it.kind == kind }

        fun summary(): String = kinds.joinToString(" ") {
            val status = when {
                it.failed -> "失败"
                it.replayed > 0 -> "重放${it.replayed}条"
                else -> "空"
            }
            "${it.kind.name}:$status"
        }
    }

    /** 重放全部类别的 pending atom（按 id FIFO）。 */
    suspend fun replayAll(): Report = Report(EntityKind.entries.map { replayKind(it) })

    /**
     * 按类别重放：FIFO 保序，遇失败即停该类别（后面的等下一轮）；
     * attempts 超上限的毒丸 atom 隔离跳过（不删、不挡队）。
     */
    private suspend fun replayKind(kind: EntityKind): KindOutcome {
        var replayed = 0
        var quarantined = 0
        val atoms = system.syncAtomDao().getAllAtoms().filter { it.entityKind == kind }
        for (atom in atoms) {
            if (atom.attempts >= MAX_REPLAY_ATTEMPTS) {
                quarantined++
                BadgerLog.w(TAG, "atom#${atom.id} $kind/${atom.syncType} 已失败${atom.attempts}次，隔离跳过")
                continue
            }
            val outcome = runCatching { replayAtom(atom) }
                .getOrElse { AtomOutcome(false, null, "重放异常: ${it.message}") }
            if (outcome.success) {
                system.syncAtomDao().deleteAtomById(atom.id)
                outcome.serverUuid?.let { remapServerUuid(kind, uuidFromPayload(atom.data), it) }
                replayed++
            } else {
                val error = outcome.error ?: "未知错误"
                system.syncAtomDao().updateReplayFailure(atom.id, atom.attempts + 1, error.take(MAX_ERROR_LENGTH))
                BadgerLog.w(TAG, "atom#${atom.id} $kind/${atom.syncType} 重放失败: $error")
                return KindOutcome(kind, replayed, quarantined, failed = true, lastError = error)
            }
        }
        return KindOutcome(kind, replayed, quarantined, failed = false, lastError = null)
    }

    private suspend fun replayAtom(atom: SyncAtom): AtomOutcome = when (atom.entityKind) {
        EntityKind.PERSON -> replayPerson(atom)
        EntityKind.COLLECTION -> replayCollection(atom)
        EntityKind.TAG -> replayTag(atom)
        EntityKind.PROFILE -> AtomOutcome(false, null, "PROFILE 重放未实现（端点未接入）")
    }

    // Person：create 服务端读 body.uuid（无重映射）；update 对应端点（服务端暂有 500 bug，会走失败重试）
    private suspend fun replayPerson(atom: SyncAtom): AtomOutcome = when (atom.syncType) {
        SyncType.INSERT, SyncType.UPDATE -> {
            val person = Json.decodeFromJsonElement<Person>(atom.data)
            val result = if (atom.syncType == SyncType.INSERT) {
                PersonApi.createPerson(person)
            } else {
                PersonApi.updatePerson(person)
            }
            result.toAtomOutcome()
        }
        SyncType.DELETE -> PersonApi.deletePerson(uuidFromPayloadOrFail(atom)).toAtomOutcomeTolerate404()
    }

    // Collection：create 服务端保留客户端 uuid（读 body.uuid），无重映射；
    // Tag：create 服务端忽略 body.uuid 自生成 → 成功后回填重映射本地与后续 pending
    private suspend fun replayCollection(atom: SyncAtom): AtomOutcome = when (atom.syncType) {
        SyncType.INSERT, SyncType.UPDATE -> {
            val collection = Json.decodeFromJsonElement<Collection>(atom.data)
            val result = if (atom.syncType == SyncType.INSERT) {
                CollectionApi.createCollection(collection)
            } else {
                CollectionApi.updateCollection(collection)
            }
            result.toAtomOutcome()
        }
        SyncType.DELETE -> CollectionApi.deleteCollection(uuidFromPayloadOrFail(atom)).toAtomOutcomeTolerate404()
    }

    private suspend fun replayTag(atom: SyncAtom): AtomOutcome = when (atom.syncType) {
        SyncType.INSERT, SyncType.UPDATE -> {
            val tag = Json.decodeFromJsonElement<Tags>(atom.data)
            val result = if (atom.syncType == SyncType.INSERT) {
                TagsApi.createTag(tag)
            } else {
                TagsApi.updateTag(tag)
            }
            result.toAtomOutcome()
        }
        SyncType.DELETE -> TagsApi.deleteTag(uuidFromPayloadOrFail(atom)).toAtomOutcomeTolerate404()
    }

    /**
     * create 类操作服务端自生成 uuid 时，把本地行与后续 pending atom 的载荷
     * 统一改写到服务端 uuid（旧 Outbox 契约的 CreateOnPush 回填）。
     */
    private suspend fun remapServerUuid(kind: EntityKind, oldUuid: Uuid?, serverUuid: Uuid) {
        if (oldUuid == null || oldUuid == serverUuid) return
        when (kind) {
            EntityKind.COLLECTION -> cache.withTransaction {
                val old = cache.collectionDao().getCollections().firstOrNull { it.uuid == oldUuid } ?: return@withTransaction
                cache.collectionDao().deleteCollection(old)
                cache.collectionDao().upsertCollection(old.copy(uuid = serverUuid))
            }
            EntityKind.TAG -> cache.withTransaction {
                val old = cache.tagsDao().getTags(oldUuid) ?: return@withTransaction
                cache.tagsDao().deleteTags(old)
                cache.tagsDao().upsertTags(old.copy(uuid = serverUuid))
            }
            else -> {}
        }
        rewritePendingPayloads(kind, oldUuid, serverUuid)
        BadgerLog.d(TAG, "$kind uuid 回填重映射: $oldUuid -> $serverUuid")
    }

    /** 后续 pending atom 载荷里引用旧 uuid 的，一并改写，避免重放打到不存在的旧 id。 */
    private suspend fun rewritePendingPayloads(kind: EntityKind, oldUuid: Uuid, newUuid: Uuid) {
        system.syncAtomDao().getAllAtoms()
            .filter { it.entityKind == kind && uuidFromPayload(it.data) == oldUuid }
            .forEach { atom ->
                val newData: JsonObject = when {
                    atom.syncType == SyncType.DELETE ->
                        buildJsonObject { put("uuid", newUuid.toString()) }
                    kind == EntityKind.COLLECTION ->
                        Json.encodeToJsonElement(
                            Json.decodeFromJsonElement<Collection>(atom.data).copy(uuid = newUuid)
                        ).jsonObject
                    kind == EntityKind.TAG ->
                        Json.encodeToJsonElement(
                            Json.decodeFromJsonElement<Tags>(atom.data).copy(uuid = newUuid)
                        ).jsonObject
                    kind == EntityKind.PERSON ->
                        Json.encodeToJsonElement(
                            Json.decodeFromJsonElement<Person>(atom.data).copy(uuid = newUuid)
                        ).jsonObject
                    else -> atom.data
                }
                system.syncAtomDao().updatePayload(atom.id, newData)
            }
    }

    private fun HttpResult.toAtomOutcome(): AtomOutcome = when (this) {
        is HttpResult.Success -> AtomOutcome(true, extractServerUuid(body), null)
        is HttpResult.Failure -> AtomOutcome(false, null, "HTTP $code ($errorType)")
    }

    /** DELETE 打到服务端已不存在的资源（404）视为成功——幂等。 */
    private fun HttpResult.toAtomOutcomeTolerate404(): AtomOutcome = when {
        this is HttpResult.Success -> AtomOutcome(true, null, null)
        this is HttpResult.Failure && code == 404 -> AtomOutcome(true, null, null)
        else -> toAtomOutcome()
    }

    private fun extractServerUuid(body: String): Uuid? = runCatching {
        Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("uuid")
            ?.jsonPrimitive?.content?.let { Uuid.parse(it) }
    }.getOrNull()

    private fun uuidFromPayload(data: JsonObject): Uuid? =
        data["uuid"]?.jsonPrimitive?.content?.let { Uuid.parse(it) }

    private fun uuidFromPayloadOrFail(atom: SyncAtom): Uuid =
        uuidFromPayload(atom.data) ?: error("atom#${atom.id} 载荷缺 uuid 字段")

    companion object {
        private const val TAG = "SyncAtomReplayerTester"
        private const val MAX_REPLAY_ATTEMPTS = 5
        private const val MAX_ERROR_LENGTH = 300
    }
}
