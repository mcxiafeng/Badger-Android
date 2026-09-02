package top.mcxiafeng.badger.sync

import android.util.Log
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
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPersonProfileEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformRows
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformsJson
import top.mcxiafeng.badger.network.CollectionDto
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.TagDto
import top.mcxiafeng.badger.network.parseServerDateMillis
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 多端增量同步引擎：`GET /api/user/sync?since=` 增量重放落 Room。
 *
 * 正确性原则：
 * - 游标只在整批成功后推进；
 * - 不认识的变更不能静默消费，否则会永久丢失同步数据；
 * - `UPDATE` 缺本地行时先按 serverId 回源完整 Person，再继续应用字段更新；
 * - 服务端游标必须单调递增，分页必须实际前进；
 * - 命中最大拉取轮数时视为失败，而不是伪装成 Done。
 */
class SyncRepository(
    private val serverApi: ServerApi,
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val tagCacheDao: TagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
    private val personProfileCacheDao: PersonProfileCacheDao,
) {

    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    suspend fun pullOnce(): SyncPullResult = withContext(Dispatchers.IO) {
        mutex.withLock { doPull() }
    }

    suspend fun pullOnceIfIdle(): SyncPullResult = withContext(Dispatchers.IO) {
        if (!started.compareAndSet(false, true)) {
            Log.d(TAG, "pullOnceIfIdle: 已在拉取中,跳过")
            return@withContext SyncPullResult.Skipped
        }
        try {
            mutex.withLock { doPull() }
        } finally {
            started.set(false)
        }
    }

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

    private companion object {
        const val TAG = "SyncRepository"
        const val MAX_PULL_ROUNDS = 50
        const val LOG_VALUE_LIMIT = 200
        val NON_LOCAL_OBJECT_NAMES = setOf("Device", "UserSettings")
    }
}

sealed interface SyncPullResult {
    data class Done(val applied: Int, val cursor: Long) : SyncPullResult {
        override fun toString(): String = "SyncPullResult(applied=$applied, cursor=$cursor)"
    }

    data class Failed(val applied: Int, val cursor: Long) : SyncPullResult

    data object Skipped : SyncPullResult
}
