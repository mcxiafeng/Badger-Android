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
import top.mcxiafeng.badger.data.cache.dao.SyncCursorDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.SyncCursorEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toContactCacheEntity
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformRows
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformsJson
import top.mcxiafeng.badger.network.CollectionDto
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.SyncChange
import top.mcxiafeng.badger.network.TagDto
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [Phase 3] 多端增量同步引擎 — `GET /api/user/sync?since=` 增量重放落 Room。
 *
 * 替代退役的 [PendingUploadExecutor]/[PendingUploadScheduler]/[ContactSyncBootstrapper]：
 * 服务端权威同步（`docs/api-handover-migration-plan.md` §C3）。
 *
 * 流程：
 * ```
 *   pullOnce():
 *     cursor = sync_cursor.lastVersion(默认 0 = 全量重放)
 *     loop:
 *       page = GET /api/user/sync?since=cursor
 *       applyChanges(page.changes)   // 任一条失败 → 整批回滚游标(下轮重试,不静默丢)
 *       cursor = page.version        // 整批成功后推进
 *       hasMore ? continue : break
 * ```
 *
 * 增量语义（服务端 `UserHistory`，owner 域版本严格单调递增）：
 * - **ADD**：[value] 为完整对象快照 → upsert 本地行（serverId=uuid）；
 * - **UPDATE**：[value] 为字段新值 JSON 文本（[fieldName] 指明改的是哪个字段）→ 按字段应用；
 * - **REMOVE**：仅 [objectId] 有效 → 按 serverId 删除本地行。
 *
 * [修复防御]：单条 change 失败**不推进游标**（Log.e + 中断整批）——宁可下次重放重复，
 * 也不静默丢失一条增量；[Mutex] 防并发 pull；[AtomicBoolean] 防启动期重复进入。
 */
class SyncRepository(
    private val serverApi: ServerApi,
    private val syncCursorDao: SyncCursorDao,
    private val contactCacheDao: ContactCacheDao,
    private val contactPlatformCacheDao: ContactPlatformCacheDao,
    private val tagCacheDao: TagCacheDao,
    private val cardCollectionCacheDao: CardCollectionCacheDao,
    private val contactTagCacheDao: ContactTagCacheDao,
) {

    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    /** 单轮拉取上限，防止游标异常时无限循环。 */
    private val MAX_PULL_ROUNDS = 50

    /**
     * 启动期/手动触发一次全量增量拉取。
     *
     * @return 成功重放并推进游标的 change 数（不含跳过/失败）；网络异常向上抛，由调用方降级。
     */
    suspend fun pullOnce(): SyncPullResult = withContext(Dispatchers.IO) {
        mutex.withLock { doPull() }
    }

    /** 尝试拉取（并发重入保护：进行中直接返回 skipped）。 */
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
        while (rounds < MAX_PULL_ROUNDS) {
            rounds++
            val page = try {
                serverApi.syncSince(cursor)
            } catch (e: Exception) {
                // 网络/鉴权异常:推进游标前的数据还没落库,下轮重拉,不丢
                Log.w(TAG, "doPull: syncSince($cursor) 失败 rounds=$rounds", e)
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }
            if (page.changes.isEmpty()) break
            val ok = applyChanges(page.changes)
            if (!ok) {
                // [修复防御]: 任一条 change 失败 → 游标不动,下轮从失败处重放
                Log.e(TAG, "doPull: 批次应用失败,游标保持 $cursor,下轮重放 changes=${page.changes.size}")
                return SyncPullResult.Failed(applied = applied, cursor = cursor)
            }
            applied += page.changes.size
            cursor = page.version
            syncCursorDao.upsert(SyncCursorEntity(lastVersion = cursor, updatedAt = System.currentTimeMillis()))
            Log.d(TAG, "doPull: 批次完成 changes=${page.changes.size} cursor=$cursor hasMore=${page.hasMore}")
            if (!page.hasMore) break
        }
        Log.d(TAG, "doPull: 完成 applied=$applied cursor=$cursor rounds=$rounds")
        return SyncPullResult.Done(applied = applied, cursor = cursor)
    }

    /** 应用一批 change；任一条抛异常 → 整体失败（游标不动）。 */
    private suspend fun applyChanges(changes: List<SyncChange>): Boolean {
        for (change in changes) {
            try {
                applyChange(change)
            } catch (e: Exception) {
                Log.e(TAG, "applyChanges: version=${change.version} type=${change.type} " +
                    "object=${change.objectName} failed", e)
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
            else -> Log.w(TAG, "applyChange: 未知 type=${change.type} version=${change.version}")
        }
    }

    // ============ ADD（value 为完整对象快照） ============

    private suspend fun applyAdd(change: SyncChange) {
        when (change.objectName) {
            "Person" -> {
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IllegalStateException("Person ADD value 非对象")
                upsertPerson(PersonDto.from(obj))
            }
            "Collection" -> {
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IllegalStateException("Collection ADD value 非对象")
                upsertCollection(CollectionDto.from(obj))
            }
            "Tag" -> {
                val obj = change.value?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IllegalStateException("Tag ADD value 非对象")
                upsertTag(TagDto.from(obj))
            }
            else -> Log.d(TAG, "applyAdd: objectName=${change.objectName} 忽略(非本端实体)")
        }
    }

    private suspend fun upsertPerson(person: PersonDto) {
        if (person.uuid.isBlank()) throw IllegalStateException("Person ADD uuid 缺失")
        val existing = contactCacheDao.getContactByServerId(person.uuid)
        if (existing != null) {
            // [修复防御]: 已存在 → 保留本地 avatarPath(磁盘文件),其余以服务端权威覆盖
            val mapped = person.toContactCacheEntity(id = existing.id, avatarPath = existing.avatarPath)
            contactCacheDao.updateContact(mapped)
            // 平台行全替换(服务端 profile.contactMap 权威)
            contactPlatformCacheDao.deleteByContact(existing.id)
            val rows = person.profile?.toPlatformRows(existing.id) ?: emptyList()
            if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
            contactCacheDao.bumpContact(existing.id)
            Log.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} platforms=${rows.size} (update)")
        } else {
            // [修复防御]: insertContact 返回自增 id,以该 id 组装实体再写平台子表 ——
            // 不能用 getContactByServerId 与 insertContact 混做 ?: 合并(两者返回类型不同,会推断成 Any)
            val newId = contactCacheDao.insertContact(person.toContactCacheEntity(id = 0L, avatarPath = null))
            contactPlatformCacheDao.deleteByContact(newId)
            val rows = person.profile?.toPlatformRows(newId) ?: emptyList()
            if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
            contactCacheDao.bumpContact(newId)
            Log.d(TAG, "upsertPerson: uuid=${person.uuid.take(8)} name=${person.name} platforms=${rows.size} (insert id=$newId)")
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
            createTime = existing?.createTime ?: System.currentTimeMillis(),
            isLocalOnly = false,
        )
        if (existing != null) {
            cardCollectionCacheDao.updateCollection(entity)
        } else {
            cardCollectionCacheDao.insertCollection(entity)
        }
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
            pinyinInitial = existing?.pinyinInitial ?: if (dto.name.isNotBlank()) PinyinUtils.getContactPinyinInitial(dto.name) else "",
            source = existing?.source ?: "manual",
            showDot = existing?.showDot ?: true,
            createTime = existing?.createTime ?: System.currentTimeMillis(),
            isLocalOnly = false,
        )
        if (existing != null) {
            tagCacheDao.updateTag(entity)
        } else {
            tagCacheDao.insertTag(entity)
        }
        // personMembers → contact_tag_cache cross-ref 重建(本地 UI 查询路径)
        rebuildTagRefs(entity, dto.personMembers)
        Log.d(TAG, "upsertTag: uuid=${dto.uuid.take(8)} name=${dto.name} members=${dto.personMembers.size}")
    }

    // ============ UPDATE（value 为字段新值，fieldName 指明字段） ============

    private suspend fun applyUpdate(change: SyncChange) {
        val fieldName = change.fieldName
        when (change.objectName) {
            "Person" -> applyPersonUpdate(change, fieldName)
            "Collection" -> applyCollectionUpdate(change, fieldName)
            "Tag" -> applyTagUpdate(change, fieldName)
            else -> Log.d(TAG, "applyUpdate: objectName=${change.objectName} 忽略")
        }
    }

    private suspend fun applyPersonUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Person UPDATE objectId 缺失")
        val local = contactCacheDao.getContactByServerId(uuid) ?: throw IllegalStateException("Person UPDATE 本地行缺失 uuid=$uuid")
        when (fieldName) {
            "name" -> {
                val newName = change.value?.takeIf { !it.isJsonNull }?.asString ?: return
                contactCacheDao.updateContact(local.copy(
                    name = newName,
                    pinyinInitial = PinyinUtils.getContactPinyinInitial(newName),
                ))
            }
            "profile" -> {
                val profileJson = change.value?.takeIf { it.isJsonObject }?.asJsonObject ?: return
                val profile = ProfileDto.from(profileJson)
                contactCacheDao.updateContact(local.copy(
                    avatarUrl = profile.avatarURL,
                    bio = profile.description,
                    platformsJson = profile.toPlatformsJson(),
                ))
                contactPlatformCacheDao.deleteByContact(local.id)
                val rows = profile.toPlatformRows(local.id)
                if (rows.isNotEmpty()) contactPlatformCacheDao.insertPlatforms(rows)
            }
            "updateTime" -> {
                // 服务端 updateTime 仅推进本地更新时间戳(展示用)
                contactCacheDao.updateContact(local.copy(updateTime = System.currentTimeMillis()))
            }
            else -> Log.d(TAG, "applyPersonUpdate: fieldName=$fieldName 忽略")
        }
        contactCacheDao.bumpContact(local.id)
    }

    private suspend fun applyCollectionUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Collection UPDATE objectId 缺失")
        val local = cardCollectionCacheDao.getCollectionByServerId(uuid) ?: throw IllegalStateException("Collection UPDATE 本地行缺失 uuid=$uuid")
        val updated = when (fieldName) {
            "name" -> local.copy(name = change.value?.takeIf { !it.isJsonNull }?.asString ?: return)
            "description" -> local.copy(description = change.value?.takeIf { !it.isJsonNull }?.asString)
            "backgroundURL" -> local.copy(coverAvatarUrl = change.value?.takeIf { !it.isJsonNull }?.asString)
            "personMembers" -> local.copy(personMembers = listToJson(parseUuidList(change.value)))
            else -> null
        }
        if (updated != null) cardCollectionCacheDao.updateCollection(updated)
    }

    private suspend fun applyTagUpdate(change: SyncChange, fieldName: String?) {
        val uuid = change.objectId ?: throw IllegalStateException("Tag UPDATE objectId 缺失")
        val local = tagCacheDao.getTagByServerId(uuid) ?: throw IllegalStateException("Tag UPDATE 本地行缺失 uuid=$uuid")
        val updated = when (fieldName) {
            "name" -> local.copy(
                name = change.value?.takeIf { !it.isJsonNull }?.asString ?: return,
                pinyinInitial = change.value?.takeIf { !it.isJsonNull }?.asString?.let { PinyinUtils.getContactPinyinInitial(it) } ?: local.pinyinInitial,
            )
            "colorHash" -> local.copy(colorHash = change.value?.takeIf { !it.isJsonNull }?.asString)
            "personMembers" -> {
                val members = parseUuidList(change.value)
                local.copy(personMembers = listToJson(members)).also {
                    rebuildTagRefs(it, members)
                }
            }
            else -> null
        }
        if (updated != null) tagCacheDao.updateTag(updated)
    }

    // ============ REMOVE（仅 objectId 有效） ============

    private suspend fun applyRemove(change: SyncChange) {
        val uuid = change.objectId ?: throw IllegalStateException("REMOVE objectId 缺失")
        when (change.objectName) {
            "Person" -> {
                val local = contactCacheDao.getContactByServerId(uuid)
                if (local != null) {
                    contactPlatformCacheDao.deleteByContact(local.id)
                    contactTagCacheDao.clearByContact(local.id)
                    contactCacheDao.deleteById(local.id)
                    Log.d(TAG, "applyRemove: Person uuid=${uuid.take(8)} 已删本地行 id=${local.id}")
                }
            }
            "Collection" -> cardCollectionCacheDao.deleteCollectionByServerId(uuid)
            "Tag" -> tagCacheDao.deleteTagByServerId(uuid)
            else -> Log.d(TAG, "applyRemove: objectName=${change.objectName} 忽略")
        }
    }

    // ============ 辅助 ============

    /** tag personMembers(uuid 列表) → contact_tag_cache cross-ref（本地 UI 查询路径）。 */
    private suspend fun rebuildTagRefs(tag: TagCacheEntity, members: List<String>) {
        if (members.isEmpty()) {
            contactTagCacheDao.clearByTag(tag.id)
            return
        }
        val contacts = contactCacheDao.getContactsByServerIds(members)
        if (contacts.isEmpty()) {
            contactTagCacheDao.clearByTag(tag.id)
            return
        }
        contactTagCacheDao.clearByTag(tag.id)
        val now = System.currentTimeMillis()
        contactTagCacheDao.insertCrossRefs(
            contacts.map { c ->
                ContactTagCacheEntity(
                    contactId = c.id,
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
            return value.asJsonArray.mapNotNull { el -> if (el.isJsonNull) null else el.asString }
        }
        if (value.isJsonObject) {
            // 服务端可能把字段值包成 {value: [...]} 之类,兜底解析
            val v = value.asJsonObject.get("value")
            if (v != null && v.isJsonArray) return v.asJsonArray.mapNotNull { it.takeIf { !it.isJsonNull }?.asString }
        }
        return emptyList()
    }

    companion object {
        private const val TAG = "SyncRepository"
    }
}

/** 一次同步拉取的结果。 */
sealed interface SyncPullResult {
    /** 已成功应用并推进游标的 change 数 + 最新游标。 */
    data class Done(val applied: Int, val cursor: Long) : SyncPullResult {
        override fun toString(): String = "SyncPullResult(applied=$applied, cursor=$cursor)"
    }

    /** 拉取/应用失败（游标未推进，可重试）。 */
    data class Failed(val applied: Int, val cursor: Long) : SyncPullResult

    /** 并发重入被跳过。 */
    data object Skipped : SyncPullResult
}
