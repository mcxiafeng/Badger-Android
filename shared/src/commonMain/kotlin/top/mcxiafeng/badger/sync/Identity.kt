package top.mcxiafeng.badger.sync

import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

enum class EntityKind {
    PERSON,
    TAG,
    COLLECTION,
}

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

enum class OutboxOpType {
    CREATE,
    PATCH,
    DELETE,
    MEMBER_ADD,
    MEMBER_REMOVE,
}

sealed class RemoteIdentity {
    
    data class Synced(val serverId: String) : RemoteIdentity()

    
    data class PendingCreate(val clientUuid: String) : RemoteIdentity()

    

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

fun rebaseCollection(
    incoming: CardCollectionCacheEntity,
    existing: CardCollectionCacheEntity,
): CardCollectionCacheEntity = incoming.copy(
    serverId = existing.serverId,
    personMembers = existing.personMembers,
    isLocalOnly = existing.isLocalOnly,
    createTime = existing.createTime,
)

fun rebaseTag(
    incoming: TagCacheEntity,
    existing: TagCacheEntity,
): TagCacheEntity = incoming.copy(
    serverId = existing.serverId,
    personMembers = existing.personMembers,
    isLocalOnly = existing.isLocalOnly,
    createTime = existing.createTime,
)
