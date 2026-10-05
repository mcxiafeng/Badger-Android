package top.mcxiafeng.badger.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import top.mcxiafeng.badger.data.system.entity.SyncAtom
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.HttpResult
import kotlin.uuid.Uuid

private const val TAG = "SyncableTester"

/** 服务端侧操作失败（拉取返回空 / 写接口非 2xx）。 */
class SyncGatewayException(
    val op: String,
    val errorType: HttpResult.ErrorType?,
    val statusCode: Int?,
) : RuntimeException("Syncable.$op 失败: type=${errorType ?: "NULL_RESPONSE"} code=${statusCode ?: "-"}")

/** 拉取返回空的 fail-loud 翻译：null 一律视为失败，绝不静默当"空清单"。 */
internal fun <T> requireBody(op: String, body: T?): T =
    body ?: throw SyncGatewayException(op, errorType = null, statusCode = null)

internal fun requireSuccess(op: String, result: HttpResult) {
    if (result is HttpResult.Failure) throw SyncGatewayException(op, result.errorType, result.code)
}

/**
 * 单查 HttpResult 三态翻译：200 带 data → 实体；200 缺 data / HTTP 404 → null（服务端确无）；
 * 其余失败抛 [SyncGatewayException]。网络抖动绝不折成"不存在"——那会把离线修改
 * 误判为"服务端已删除"而直接删本地行（丢数据）。
 */
internal inline fun <T> decodeExisting(op: String, result: HttpResult, decode: (JsonObject) -> T): T? =
    when (result) {
        is HttpResult.Success ->
            Json.parseToJsonElement(result.body).jsonObject["data"]?.jsonObject?.let(decode)
        is HttpResult.Failure ->
            if (result.code == 404) null
            else throw SyncGatewayException(op, result.errorType, result.code)
    }

/**
 * 单类实体的同步通道——同步模块的内部缝：生产实现走真实库与 API，测试注入假实现。
 * 上半部是实体原语，下半部的默认实现即判定表（只写一遍）；
 * 原语失败统一抛 [SyncGatewayException] 由引擎记账留队，其余异常即 bug 直接崩。
 */
interface Syncable<T> {

    val kind: EntityKind

    fun uuidOf(entity: T): Uuid

    /** 冲突弹窗里展示的名字。 */
    fun labelOf(entity: T): String

    fun encode(entity: T): JsonObject

    fun decode(data: JsonObject): T

    /** 拉取服务端全量清单（profile 这类单行实体返回单元素表）。 */
    suspend fun fetchAll(profileUuid: Uuid): List<T>

    suspend fun localUpsertAll(rows: List<T>)

    suspend fun localDelete(uuid: Uuid)

    /** 本地表的全量清理（仅"表内容 == 服务端清单"的实体实现；profile 等承载缓存的一律空实现）。 */
    suspend fun localDeleteAll()

    suspend fun localDeleteNotIn(keep: List<Uuid>)

    suspend fun pushCreate(entity: T)

    suspend fun pushUpdate(entity: T)

    suspend fun pushDelete(uuid: Uuid)

    /** 按 uuid 查服务端现值：请求失败抛异常，服务端确无此实体才返回 null。 */
    suspend fun serverGet(uuid: Uuid): T?

    /** 快照落库：离线删除的行保持删除；pending 实体避让；其余服务端赢。 */
    suspend fun persist(rows: List<T>, pending: PendingIndex) {
        pending.deleteUuids(kind).forEach { uuid ->
            BadgerLog.d(TAG, "落库避让：$kind=$uuid 有离线删除意图，本地行保持删除")
            localDelete(uuid)
        }
        val skip = pending.all(kind)
        val remote = rows.filter { uuidOf(it) !in skip }
        if (remote.isNotEmpty()) localUpsertAll(remote)
        val keep = rows.map { uuidOf(it) } + pending.protected(kind)
        if (keep.isEmpty()) localDeleteAll() else localDeleteNotIn(keep)

    }

    /**
     * 判定表：每条 atom 独立用 [serverGet] 查服务端现状再定性——
     * Person/Profile 走各自的单查接口（GET /persons/{uuid} 等，用户裁决的查重方式），
     * Collection/Tags 无单查接口、在各自 serverGet 里走清单过滤。
     * INSERT 查存在（有→采纳/无→推送）、DELETE 查存在（有→推送/无→幂等销账）、
     * UPDATE 取现值比 from 基线（一致→推送 to/偏离→挂冲突）。
     */
    suspend fun reconcile(atom: SyncAtom): ReconcileResult {
        val uuid = AtomPayload.uuidOf(atom.syncType, atom.data)
        val server = serverGet(uuid)
        return when (atom.syncType) {
            SyncType.INSERT ->
                if (server != null) {
                    BadgerLog.d(TAG, "$kind=$uuid 服务端已存在同 uuid，采纳服务端 (atom#${atom.id})")
                    localUpsertAll(listOf(server))
                    ReconcileResult.Settled
                } else {
                    push(atom)
                    ReconcileResult.Settled
                }

            SyncType.DELETE ->
                if (server != null) {
                    push(atom)
                    ReconcileResult.Settled
                } else {
                    BadgerLog.d(TAG, "$kind=$uuid 服务端已不存在，幂等销账 DELETE atom#${atom.id}")
                    localDelete(uuid)
                    ReconcileResult.Settled
                }

            SyncType.UPDATE ->
                if (server == null) {
                    BadgerLog.d(TAG, "$kind=$uuid 服务端已删除，采纳服务端并删本地行 (atom#${atom.id})")
                    localDelete(uuid)
                    ReconcileResult.Settled
                } else {
                    val serverJson = encode(server)
                    when {
                        // 基线未被他人动过：无冲突，推 to
                        AtomPayload.fromOf(atom.data) == serverJson -> {
                            BadgerLog.d(TAG, "$kind=$uuid 服务端现值与基线一致，无冲突直接推送 (atom#${atom.id})")
                            push(atom)
                            ReconcileResult.Settled
                        }
                        // 服务端已是目标值：上轮推送成功但销账前中断——补销账不重推
                        AtomPayload.toOf(atom.data) == serverJson -> {
                            BadgerLog.d(TAG, "$kind=$uuid 服务端已是目标值，补销账不重推 (atom#${atom.id})")
                            ReconcileResult.Settled
                        }
                        else -> {
                            BadgerLog.w(TAG, "冲突：$kind=$uuid 服务端现值偏离基线，等待用户裁决 (atom#${atom.id})")
                            ReconcileResult.Conflicted(
                                SyncConflict(atom.id, kind, uuid, labelOf(server)),
                            )
                        }
                    }
                }
        }
    }

    /** 推送一条意图（INSERT 载荷即实体全文，UPDATE 取 to，DELETE 取顶层 uuid）。 */
    suspend fun push(atom: SyncAtom) {
        when (atom.syncType) {
            SyncType.INSERT -> pushCreate(decode(atom.data))
            SyncType.UPDATE -> pushUpdate(decode(AtomPayload.toOf(atom.data)))
            SyncType.DELETE -> pushDelete(AtomPayload.uuidOf(atom.syncType, atom.data))
        }
    }

    /** 「用服务端的」裁决：服务端现值拉回本地覆盖；弹窗期间实体已被他端删除则落实为删除本地行。 */
    suspend fun applyServer(atom: SyncAtom) {
        val uuid = AtomPayload.uuidOf(atom.syncType, atom.data)
        val server = serverGet(uuid)
        if (server == null) {
            BadgerLog.d(TAG, "$kind=$uuid 服务端已不存在，「用服务端的」落实为删除本地行 (atom#${atom.id})")
            localDelete(uuid)
            return
        }
        localUpsertAll(listOf(server))
    }
}
