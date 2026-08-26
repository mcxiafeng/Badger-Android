package top.mcxiafeng.badger.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.TagExport
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.utils.PinyinUtils

/**
 * 标签仓库实现。
 *
 * [Phase 3] 直推改造：写操作（upsertTag / renameTag / setTagColor / deleteTag / 成员关联）
 * 本地落 `tags_cache` 后**直推** `/api/user/tags` 新契约（uuid / colorHash / personMembers +
 * 成员子接口），不再走 PendingUpload 队列。
 *
 * 关键语义（对齐 `docs/api-handover-migration-plan.md` §C2）：
 * - `id:Long` → `serverId:uuid`（服务端分配，回填本列）；
 * - `color:0xAARRGGBB Long` → `colorHash:"0xRRGGBBAA" hex`（本地展示仍用 color Long）；
 * - 成员关联 `contact_tag_cache` 本地立即可见 + 直推成员子接口（POST/DELETE
 *   `/tags/{uuid}/members/{personUuid}`），失败仅污染该条（与 merge 的整批拒绝不同）。
 *
 * [修复防御]：所有直推失败**不阻塞本地保存**（本地最终一致，sync 兜底），但必须打日志——
 * 有观测的降级，不是静默吞错。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::TagRepositoryImpl) { bind<TagRepository>() }`。
 */
class TagRepositoryImpl(
    private val tagDao: TagCacheDao,
    private val contactTagDao: ContactTagCacheDao,
    private val contactDao: ContactCacheDao,
    private val db: AppDatabase,
    // [Phase 3] 直推新 Java /api 契约
    private val serverApi: ServerApi,
) : TagRepository {

    // ========== 标签 CRUD ==========

    override fun observeAllTags(): Flow<List<TagCacheEntity>> = tagDao.observeAllTags()

    override suspend fun getAllTagsOnce(): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        tagDao.getAllTagsOnce()
    }

    override suspend fun getTagById(id: Long): TagCacheEntity? = withContext(Dispatchers.IO) {
        tagDao.getTagById(id)
    }

    /**
     * 同名复用：已存在直接返回 id；否则本地插入 + 直推 `POST /api/user/tags`（uuid 回填）。
     * 离线直推失败 → 本地 `isLocalOnly=true` 兜底行（下次编辑 create-on-push 补推）。
     */
    override suspend fun upsertTag(
        name: String,
        color: Long,
        source: String,
    ): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        tagDao.getTagByName(trimmed)?.let { existing ->
            Log.d(TAG, "upsertTag: reuse existing tag id=${existing.id} name='$trimmed'")
            return@withContext existing.id
        }
        val now = System.currentTimeMillis()
        val colorHash = colorToHash(color)
        val tag = TagCacheEntity(
            name = trimmed,
            color = color,
            colorHash = colorHash,
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
            source = source,
            createTime = now,
        )
        val newId = tagDao.insertTag(tag)
        Log.d(TAG, "upsertTag: created new tag id=$newId name='$trimmed' source=$source")
        // [Phase 3] 直推 create → 服务端分配 uuid
        val serverUuid = try {
            serverApi.createTag(name = trimmed, colorHash = colorHash, personMembers = null)
        } catch (e: Exception) {
            // [修复防御]: 离线直推失败 → 本地 isLocalOnly 兜底,下次编辑 create-on-push 补推
            Log.w(TAG, "upsertTag: createTag 失败,落本地 isLocalOnly 兜底 id=$newId name='$trimmed'", e)
            null
        }
        if (serverUuid != null) {
            tagDao.updateTag(tag.copy(id = newId, serverId = serverUuid, isLocalOnly = false))
        }
        newId
    }

    override suspend fun renameTag(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.renameTag(
            id = id,
            newName = trimmed,
            newPinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
        )
        pushTagPatch(current, name = trimmed)
    }

    override suspend fun recomputePinyinInitial(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        val newPinyin = PinyinUtils.getContactPinyinInitial(current.name)
        tagDao.updatePinyinInitial(id, newPinyin)
        // pinyinInitial 是本地展示字段，服务端无对应列，不推送
        Log.d(TAG, "recomputePinyinInitial: id=$id newPinyin=$newPinyin (本地字段,不推服务端)")
    }

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.deleteTagById(id)
        // [Phase 3] 直推 DELETE（404 幂等成功由 ServerApi 处理）
        val uuid = current.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteTag(uuid)
            } catch (e: Exception) {
                Log.w(TAG, "deleteTag: DELETE /api/user/tags/$uuid 失败(本地已删,sync 兜底)", e)
            }
        } else {
            Log.w(TAG, "deleteTag: id=$id isLocalOnly(无 serverId),仅本地删除")
        }
    }

    override suspend fun setTagDotVisible(id: Long, show: Boolean) = withContext(Dispatchers.IO) {
        // [修复防御]: setTagDotVisible 仅改本地 dot_visible 字段,不入队/不推送(服务端无 dot 概念)。
        tagDao.setTagDotVisible(id, show)
    }

    override suspend fun setTagColor(id: Long, color: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        if (current.color == color) {
            Log.d(TAG, "setTagColor: tagId=$id no change, skip")
            return@withContext
        }
        tagDao.updateTag(current.copy(color = color, colorHash = colorToHash(color)))
        val affectedContactIds = contactTagDao.getContactIdsByTag(id)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        // [Phase 3] 直推 colorHash
        pushTagPatch(current, colorHash = colorToHash(color))
        Log.d(TAG, "setTagColor: tagId=$id color=0x${color.toString(16)} affected=${affectedContactIds.size}")
    }

    override suspend fun searchTagsByName(query: String): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        tagDao.searchTagsByName(query)
    }

    override suspend fun searchTagsFts(query: String, limit: Int): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        tagDao.searchTagsByName(query).take(limit)
    }

    override suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long): Unit = withContext(Dispatchers.IO) {
        require(fromTagId != toTagId) { "fromTagId and toTagId must differ" }
        val fromContactIds = contactTagDao.getContactIdsByTag(fromTagId)
        if (fromContactIds.isNotEmpty()) {
            contactTagDao.insertCrossRefs(
                fromContactIds.map { ContactTagCacheEntity(contactId = it, tagId = toTagId) }
            )
            fromContactIds.forEach { contactDao.bumpContact(it) }
            // [Phase 3] 目标 tag 补成员
            fromContactIds.forEach { pushTagMember(toTagId, it) }
        }
        val fromTag = tagDao.getTagById(fromTagId)
        tagDao.deleteTagById(fromTagId)
        if (fromTag != null) {
            val uuid = fromTag.serverId?.takeIf { it.isNotBlank() }
            if (uuid != null) {
                try {
                    serverApi.deleteTag(uuid)
                } catch (e: Exception) {
                    Log.w(TAG, "reassignTagUsage: DELETE tag $uuid 失败(本地已删)", e)
                }
            }
        }
        Log.d(TAG, "reassignTagUsage: from=$fromTagId to=$toTagId affectedContacts=${fromContactIds.size}")
    }

    override suspend fun forceDeleteTag(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val affectedContactIds = contactTagDao.getContactIdsByTag(tagId)
        val current = tagDao.getTagById(tagId)
        tagDao.deleteTagById(tagId)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        if (current != null) {
            val uuid = current.serverId?.takeIf { it.isNotBlank() }
            if (uuid != null) {
                try {
                    serverApi.deleteTag(uuid)
                } catch (e: Exception) {
                    Log.w(TAG, "forceDeleteTag: DELETE tag $uuid 失败(本地已删)", e)
                }
            }
        }
        Log.d(TAG, "forceDeleteTag: tagId=$tagId affectedContacts=${affectedContactIds.size}")
        affectedContactIds
    }

    // ========== 联系人 ↔ 标签 关联 ==========

    override fun observeTagsByContact(contactId: Long): Flow<List<TagCacheEntity>> =
        contactTagDao.observeTagIdsByContact(contactId).map { tagIds ->
            if (tagIds.isEmpty()) emptyList()
            else tagDao.searchTagsByIds(tagIds)
        }

    override suspend fun getTagsByContact(contactId: Long): List<TagCacheEntity> =
        withContext(Dispatchers.IO) {
            val tagIds = contactTagDao.getTagIdsByContact(contactId)
            if (tagIds.isEmpty()) emptyList() else tagDao.searchTagsByIds(tagIds)
        }

    override suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity> =
        withContext(Dispatchers.IO) {
            val contactIds = contactTagDao.getContactIdsByTag(tagId)
            if (contactIds.isEmpty()) return@withContext emptyList()
            contactIds.mapNotNull { contactDao.getContactById(it) }
        }

    override suspend fun addTagToContact(contactId: Long, tagId: Long): Unit = withContext(Dispatchers.IO) {
        contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId, tagId))
        bumpContact(contactId)
        pushTagMember(tagId, contactId)
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>): Unit =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext
            contactTagDao.insertCrossRefs(tagIds.map { ContactTagCacheEntity(contactId, it) })
            bumpContact(contactId)
            tagIds.forEach { pushTagMember(it, contactId) }
        }

    override suspend fun removeTagFromContact(contactId: Long, tagId: Long): Unit =
        withContext(Dispatchers.IO) {
            contactTagDao.removeCrossRef(contactId, tagId)
            bumpContact(contactId)
            pushTagMemberRemove(tagId, contactId)
        }

    override suspend fun clearContactTags(contactId: Long): Unit = withContext(Dispatchers.IO) {
        val refs = contactTagDao.getCrossRefsForContacts(listOf(contactId))
        contactTagDao.clearContactTags(contactId)
        bumpContact(contactId)
        // [Phase 3] 逐个直推 remove 成员（失败仅污染该条）
        refs.forEach { ref -> pushTagMemberRemove(ref.tagId, contactId) }
    }

    override suspend fun clearContactTagsBySource(contactId: Long, source: String): Int = withContext(Dispatchers.IO) {
        val refs = contactTagDao.getCrossRefsForContacts(listOf(contactId))
        contactTagDao.clearCrossRefsBySource(contactId, source)
        bumpContact(contactId)
        // [Phase 3] 仅对被清 source 的 ref 直推 remove
        refs.filter { it.source == source }.forEach { ref -> pushTagMemberRemove(ref.tagId, contactId) }
        Log.d(TAG, "clearContactTagsBySource: contact=$contactId source=$source")
        0
    }

    override suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCacheEntity> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyList()
            contactTagDao.getCrossRefsForContacts(contactIds)
        }

    override suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<TagCacheEntity>> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyMap()
            val refs = contactTagDao.getCrossRefsForContacts(contactIds)
            val tagIds = refs.map { it.tagId }.distinct()
            if (tagIds.isEmpty()) return@withContext emptyMap()
            val tags = tagDao.searchTagsByIds(tagIds).associateBy { it.id }
            refs.groupBy { it.contactId }.mapValues { entry ->
                entry.value.mapNotNull { tags[it.tagId] }
            }
        }

    override fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<TagCacheEntity>>> {
        if (contactIds.isEmpty()) return flowOf(emptyMap())
        return contactTagDao.observeCrossRefsForContacts(contactIds).map { refs ->
            val tagIds = refs.map { it.tagId }.distinct()
            if (tagIds.isEmpty()) emptyMap()
            else tagDao.searchTagsByIds(tagIds).associateBy { it.id }.let { tags ->
                refs.groupBy { it.contactId }.mapValues { entry ->
                    entry.value.mapNotNull { tags[it.tagId] }
                }
            }
        }
    }

    override suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<AiTagGenerator.TagCandidate>,
        source: String,
    ) = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext
        val distinct = selected.distinctBy { it.name }
        db.withTransaction {
            for (cand in distinct) {
                val tagId = upsertTag(name = cand.name, color = 0xFF1976D2L, source = source)
                contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId = contactId, tagId = tagId))
            }
        }
        bumpContact(contactId)
        // [Phase 3] 事务后逐条直推成员
        val created = distinct.mapNotNull { tagDao.getTagByName(it.name) }
        created.forEach { pushTagMember(it.id, contactId) }
    }

    override suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<TagExport>,
        now: Long,
    ): Unit = withContext(Dispatchers.IO) {
        if (tagExports.isEmpty()) return@withContext
        db.withTransaction {
            for (t in tagExports) {
                val tagId = upsertTag(name = t.name, color = t.color, source = t.source)
                contactTagDao.insertCrossRef(
                    ContactTagCacheEntity(
                        contactId = contactId,
                        tagId = tagId,
                        source = t.source,
                        confidence = t.confidence,
                        createTime = now,
                    )
                )
            }
        }
        bumpContact(contactId)
        val created = tagExports.mapNotNull { tagDao.getTagByName(it.name) }
        created.forEach { pushTagMember(it.id, contactId) }
    }

    private suspend fun bumpContact(contactId: Long) {
        contactDao.bumpContact(contactId)
    }

    // ========== [Phase 3] 直推辅助 ==========

    /**
     * 直推 `PUT /api/user/tags/{uuid}`，仅传非 null 字段。
     * [current] 用于取 serverId；无 serverId（isLocalOnly）时跳过（本地已存，create-on-push 补推）。
     */
    private suspend fun pushTagPatch(current: TagCacheEntity, name: String? = null, colorHash: String? = null) {
        val uuid = current.serverId?.takeIf { it.isNotBlank() }
        if (uuid == null) {
            Log.w(TAG, "pushTagPatch: tagId=${current.id} 无 serverId,跳过(待 create-on-push)")
            return
        }
        try {
            serverApi.patchTag(uuid, name = name, colorHash = colorHash)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagPatch: PUT tag $uuid 失败(本地已保存)", e)
        }
    }

    /** 直推加成员 `POST /tags/{uuid}/members/{personUuid}`。缺 uuid 跳过，失败仅日志。 */
    private suspend fun pushTagMember(tagId: Long, contactId: Long) {
        val tagUuid = tagDao.getTagById(tagId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addTagMember(tagUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagMember: add member 失败(本地已存,sync 兜底) tag=$tagUuid person=$personUuid", e)
        }
    }

    /** 直推移除成员 `DELETE /tags/{uuid}/members/{personUuid}`。 */
    private suspend fun pushTagMemberRemove(tagId: Long, contactId: Long) {
        val tagUuid = tagDao.getTagById(tagId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.removeTagMember(tagUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagMemberRemove: remove member 失败(本地已删,sync 兜底) tag=$tagUuid person=$personUuid", e)
        }
    }

    /**
     * 本地 `color:0xAARRGGBB Long` → 服务端 `colorHash:"0xRRGGBBAA" hex`。
     * 对齐 `TagCacheEntity.colorHash` 注释约定（客户端统一 `0xRRGGBBAA`）。
     */
    private fun colorToHash(color: Long): String {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return "0x%02X%02X%02X%02X".format(r, g, b, a)
    }

    private companion object {
        const val TAG = "TagRepository"
    }
}
