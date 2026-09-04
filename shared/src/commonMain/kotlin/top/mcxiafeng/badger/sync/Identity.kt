package top.mcxiafeng.badger.sync

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

/**
 * 同步实体种类。Outbox / SyncEngine 的消费维度（Person / Tag / Collection 走同一套
 * 幂等与重放语义），对应规格 §3.1。
 */
enum class EntityKind {
    PERSON,
    TAG,
    COLLECTION,
}

/** OutboxStore 的公开读取模型（payload 已解析为 kotlinx [JsonObject]）。[KMP K08-B] 迁 common 供 ServerApi 契约引用。 */
data class OutboxOp(
    val id: Long,
    val entityKind: EntityKind,
    val localId: Long,
    val remoteId: String?,
    val op: OutboxOpType,
    val payload: JsonObject,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

/**
 * Outbox op 类型（规格 §3.1）。[KMP K08-B] 迁 common 供 ServerApi 契约引用。
 *
 * - `CREATE` / `PATCH`：可合并 op，同 `(entityKind, localId)` 至多一行（靠 mergeKey 唯一索引认领）；
 * - `DELETE`：入队即取消同实体未发的 CREATE/PATCH；
 * - `MEMBER_ADD` / `MEMBER_REMOVE`：**不合并**，按 createdAt FIFO 逐条重放
 *   （成员子接口是独立幂等调用，add→remove→add 的中间态不可折叠）。
 */
enum class OutboxOpType {
    CREATE,
    PATCH,
    DELETE,
    MEMBER_ADD,
    MEMBER_REMOVE,
}

/**
 * `serverId` 两义字段的类型化（规格 §3.2，根治 R1/R2）：
 *
 * - `isLocalOnly=false` 时 `serverId` 承载 **服务端 uuid**（已同步）；
 * - `isLocalOnly=true` 时 `serverId` 承载 **待创建的 clientUuid**（幂等键，重试必须复用）。
 *
 * 写路径与后续 Outbox / CreateOnPush 一律通过 [RemoteIdentity] 判定身份，禁止再裸判
 * `serverId != null`。
 */
sealed class RemoteIdentity {
    /** 已与服务端同步；[serverId] 是服务端 uuid。 */
    data class Synced(val serverId: String) : RemoteIdentity()

    /** 本地已创建、服务端未确认；[clientUuid] 是持久化的幂等键，重试复用、禁止重新生成。 */
    data class PendingCreate(val clientUuid: String) : RemoteIdentity()

    /**
     * 无任何远端身份。**仅迁移存量数据允许出现，新写入禁止产出**：
     * 本地新建必须当场生成 clientUuid 落盘到 `serverId` + `isLocalOnly=true`。
     */
    data object Unidentified : RemoteIdentity()
}

fun ContactCacheEntity.identity(): RemoteIdentity = when {
    !isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.Synced(serverId!!)
    isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.PendingCreate(serverId!!)
    else -> RemoteIdentity.Unidentified
}

fun TagCacheEntity.identity(): RemoteIdentity = when {
    !isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.Synced(serverId!!)
    isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.PendingCreate(serverId!!)
    else -> RemoteIdentity.Unidentified
}

fun CardCollectionCacheEntity.identity(): RemoteIdentity = when {
    !isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.Synced(serverId!!)
    isLocalOnly && !serverId.isNullOrBlank() -> RemoteIdentity.PendingCreate(serverId!!)
    else -> RemoteIdentity.Unidentified
}

/**
 * 投影 → 实体的唯一合法路径（规格 §3.2，F3 的结构性修复）。
 *
 * UI 投影（如 `CardCollectionWithCount.toCacheEntity()`）不带 identity 字段，全行 `@Update`
 * 会抹掉 `serverId` / `personMembers` / `isLocalOnly`，导致「编辑名片夹孤立服务端行 +
 * 同步游标卡死」。任何写路径在落 DAO 前必须用 DB existing 行 rebase 一次：
 * identity 字段以 existing 为准，业务字段按 incoming。
 */
fun rebaseCollection(
    incoming: CardCollectionCacheEntity,
    existing: CardCollectionCacheEntity,
): CardCollectionCacheEntity = incoming.copy(
    serverId = existing.serverId,
    personMembers = existing.personMembers,
    isLocalOnly = existing.isLocalOnly,
    createTime = existing.createTime,
)

/** Tag 版 [rebaseCollection]：Tag 写路径同样强制 rebase。 */
fun rebaseTag(
    incoming: TagCacheEntity,
    existing: TagCacheEntity,
): TagCacheEntity = incoming.copy(
    serverId = existing.serverId,
    personMembers = existing.personMembers,
    isLocalOnly = existing.isLocalOnly,
    createTime = existing.createTime,
)
