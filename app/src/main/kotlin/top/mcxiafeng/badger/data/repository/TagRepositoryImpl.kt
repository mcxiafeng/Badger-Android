package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.utils.BadgerLog
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.importer.TagExport
import top.mcxiafeng.badger.data.cache.dao.ContactCacheDao
import top.mcxiafeng.badger.data.cache.dao.ContactTagCacheDao
import top.mcxiafeng.badger.data.cache.dao.TagCacheDao
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.sync.RemoteIdentity
import top.mcxiafeng.badger.sync.identity
import top.mcxiafeng.badger.sync.rebaseTag
import top.mcxiafeng.badger.shared.util.randomUuid
import top.mcxiafeng.badger.utils.PinyinUtils

/**
 * 标签仓库实现。
 *
 * 本地状态先写入 Room，再在事务之外直推服务端；网络调用不会占用数据库事务。
 */
class TagRepositoryImpl(
    private val tagDao: TagCacheDao,
    private val contactTagDao: ContactTagCacheDao,
    private val contactDao: ContactCacheDao,
    private val db: AppDatabase,
    private val serverApi: ServerApi,
) : TagRepository {

    override fun observeAllTags(): Flow<List<TagCacheEntity>> = tagDao.observeAllTags()

    override suspend fun getAllTagsOnce(): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        tagDao.getAllTagsOnce()
    }

    override suspend fun getTagById(id: Long): TagCacheEntity? = withContext(Dispatchers.IO) {
        tagDao.getTagById(id)
    }

    override suspend fun upsertTag(name: String, color: Long, source: String): Long =
        withContext(Dispatchers.IO) {
            val tagId = upsertTagLocal(name, color, source)
            ensureTagCreateEnqueued(tagId)
            tagId
        }

    override suspend fun renameTag(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        val current = tagDao.getTagById(id) ?: return@withContext
        // [T08] Tag 写路径统一 rebase：identity 字段永远以 DB existing 为准
        tagDao.updateTag(
            rebaseTag(
                current.copy(name = trimmed, pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed)),
                current,
            )
        )
        pushTagPatch(current, name = trimmed)
    }

    override suspend fun recomputePinyinInitial(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.updatePinyinInitial(id, PinyinUtils.getContactPinyinInitial(current.name))
    }

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        val uuid = current.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                // [T12b] DELETE 只入队 + kick；本地删除不再被网络失败阻塞
                serverApi.deleteTag(id, uuid)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "deleteTag: 入队失败; keep local tag id=$id", e)
                return@withContext
            }
        }
        tagDao.deleteTagById(id)
    }

    override suspend fun setTagDotVisible(id: Long, show: Boolean) = withContext(Dispatchers.IO) {
        tagDao.setTagDotVisible(id, show)
    }

    override suspend fun setTagColor(id: Long, color: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        if (current.color == color) return@withContext
        val colorHash = colorToHash(color)
        // [T08] Tag 写路径统一 rebase：identity 字段永远以 DB existing 为准
        tagDao.updateTag(rebaseTag(current.copy(color = color, colorHash = colorHash), current))
        contactTagDao.getContactIdsByTag(id).forEach { contactDao.bumpContact(it) }
        pushTagPatch(current, colorHash = colorHash)
    }

    override suspend fun searchTagsByName(query: String): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else tagDao.searchTagsByName(query)
    }

    override suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long): Unit = withContext(Dispatchers.IO) {
        require(fromTagId != toTagId) { "fromTagId and toTagId must differ" }
        val fromTag = tagDao.getTagById(fromTagId) ?: return@withContext
        val fromContactIds = contactTagDao.getContactIdsByTag(fromTagId)
        if (fromContactIds.isNotEmpty()) {
            contactTagDao.insertCrossRefs(fromContactIds.map { ContactTagCacheEntity(it, toTagId) })
            fromContactIds.forEach { contactDao.bumpContact(it) }
            fromContactIds.forEach { pushTagMember(toTagId, it) }
        }
        val uuid = fromTag.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteTag(fromTagId, uuid)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "reassignTagUsage: DELETE 入队失败; keeping source tag id=$fromTagId", e)
                return@withContext
            }
        }
        tagDao.deleteTagById(fromTagId)
    }

    override suspend fun forceDeleteTag(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val affectedContactIds = contactTagDao.getContactIdsByTag(tagId)
        val current = tagDao.getTagById(tagId)
        val uuid = current?.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteTag(tagId, uuid)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "forceDeleteTag: DELETE 入队失败; keep local tag id=$tagId", e)
                return@withContext affectedContactIds
            }
        }
        tagDao.deleteTagById(tagId)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        affectedContactIds
    }

    override fun observeTagsByContact(contactId: Long): Flow<List<TagCacheEntity>> =
        contactTagDao.observeTagIdsByContact(contactId).map { tagIds ->
            if (tagIds.isEmpty()) emptyList() else tagDao.searchTagsByIds(tagIds)
        }

    override suspend fun getTagsByContact(contactId: Long): List<TagCacheEntity> =
        withContext(Dispatchers.IO) {
            val tagIds = contactTagDao.getTagIdsByContact(contactId)
            if (tagIds.isEmpty()) emptyList() else tagDao.searchTagsByIds(tagIds)
        }

    override suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity> = withContext(Dispatchers.IO) {
        contactDao.getContactsByTag(tagId)
    }

    override suspend fun addTagToContact(contactId: Long, tagId: Long): Unit = withContext(Dispatchers.IO) {
        contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId, tagId))
        contactDao.bumpContact(contactId)
        pushTagMember(tagId, contactId)
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>): Unit = withContext(Dispatchers.IO) {
        if (tagIds.isEmpty()) return@withContext
        contactTagDao.insertCrossRefs(tagIds.map { ContactTagCacheEntity(contactId, it) })
        contactDao.bumpContact(contactId)
        tagIds.forEach { pushTagMember(it, contactId) }
    }

    override suspend fun removeTagFromContact(contactId: Long, tagId: Long): Unit = withContext(Dispatchers.IO) {
        contactTagDao.removeCrossRef(contactId, tagId)
        contactDao.bumpContact(contactId)
        pushTagMemberRemove(tagId, contactId)
    }

    override suspend fun clearContactTags(contactId: Long): Unit = withContext(Dispatchers.IO) {
        val refs = contactTagDao.getCrossRefsForContacts(listOf(contactId))
        contactTagDao.clearContactTags(contactId)
        contactDao.bumpContact(contactId)
        refs.forEach { pushTagMemberRemove(it.tagId, contactId) }
    }

    override suspend fun clearContactTagsBySource(contactId: Long, source: String): Int = withContext(Dispatchers.IO) {
        val refs = contactTagDao.getCrossRefsForContacts(listOf(contactId))
        val clearedCount = refs.count { it.source == source }
        contactTagDao.clearCrossRefsBySource(contactId, source)
        contactDao.bumpContact(contactId)
        refs.filter { it.source == source }.forEach { pushTagMemberRemove(it.tagId, contactId) }
        clearedCount
    }

    override suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCacheEntity> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) emptyList() else contactTagDao.getCrossRefsForContacts(contactIds)
        }

    override suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<TagCacheEntity>> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyMap()
            val refs = contactTagDao.getCrossRefsForContacts(contactIds)
            val tagIds = refs.map { it.tagId }.distinct()
            if (tagIds.isEmpty()) return@withContext emptyMap()
            val tags = tagDao.searchTagsByIds(tagIds).associateBy { it.id }
            refs.groupBy { it.contactId }.mapValues { (_, refsForContact) ->
                refsForContact.mapNotNull { tags[it.tagId] }
            }
        }

    override fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<TagCacheEntity>>> {
        if (contactIds.isEmpty()) return flowOf(emptyMap())
        return contactTagDao.observeCrossRefsForContacts(contactIds).map { refs ->
            val tagIds = refs.map { it.tagId }.distinct()
            if (tagIds.isEmpty()) emptyMap()
            else {
                val tags = tagDao.searchTagsByIds(tagIds).associateBy { it.id }
                refs.groupBy { it.contactId }.mapValues { (_, refsForContact) ->
                    refsForContact.mapNotNull { tags[it.tagId] }
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
        val distinct = selected.distinctBy { it.name.trim() }.filter { it.name.isNotBlank() }
        if (distinct.isEmpty()) return@withContext
        val tagIds = db.withTransaction {
            distinct.map { candidate ->
                val tagId = upsertTagLocal(candidate.name, 0xFF1976D2L, source)
                contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId, tagId))
                tagId
            }
        }
        contactDao.bumpContact(contactId)
        tagIds.distinct().forEach {
            ensureTagCreateEnqueued(it)
            pushTagMember(it, contactId)
        }
    }

    override suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<TagExport>,
        now: Long,
    ): Unit = withContext(Dispatchers.IO) {
        if (tagExports.isEmpty()) return@withContext
        val distinct = tagExports
            .map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
        if (distinct.isEmpty()) return@withContext
        val tagIds = db.withTransaction {
            distinct.map { t ->
                val tagId = upsertTagLocal(t.name, t.color, t.source)
                contactTagDao.insertCrossRef(
                    ContactTagCacheEntity(contactId, tagId, t.source, t.confidence, t.createTime.takeIf { it != 0L } ?: now)
                )
                tagId
            }
        }
        contactDao.bumpContact(contactId)
        tagIds.distinct().forEach {
            ensureTagCreateEnqueued(it)
            pushTagMember(it, contactId)
        }
    }

    private suspend fun upsertTagLocal(name: String, color: Long, source: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        tagDao.getTagByName(trimmed)?.let { return it.id }
        // [T14/T07] 新写入禁止 Unidentified：clientUuid 首次创建即生成并落盘（isLocalOnly 默认 true），
        // CREATE 重放/重试必须复用同一 uuid
        return tagDao.insertTag(
            TagCacheEntity(
                name = trimmed,
                serverId = randomUuid(),
                color = color,
                colorHash = colorToHash(color),
                pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
                source = source,
                createTime = System.currentTimeMillis(),
            )
        )
    }

    /**
     * [T14] 确保标签的 CREATE 意图已入队，返回 PATCH/MEMBER 可用的 remoteId。
     * Synced → serverId；PendingCreate → 复用 clientUuid（幂等，重复入队被 mergeKey 忽略）；
     * Unidentified（存量行）→ 现场生成并落盘后再入队。
     */
    private suspend fun ensureTagCreateEnqueued(tagId: Long): String? {
        val current = tagDao.getTagById(tagId) ?: return null
        val identity = current.identity()
        val remoteId = when (identity) {
            is RemoteIdentity.Synced -> identity.serverId
            is RemoteIdentity.PendingCreate -> identity.clientUuid
            is RemoteIdentity.Unidentified -> randomUuid()
        }
        if (identity is RemoteIdentity.Unidentified) {
            tagDao.updateTag(current.copy(serverId = remoteId, isLocalOnly = true))
        }
        if (identity !is RemoteIdentity.Synced) {
            try {
                serverApi.enqueueCreateTag(current.id, current.name, current.colorHash, remoteId)
            } catch (e: Exception) {
                BadgerLog.w(TAG, "ensureTagCreateEnqueued: tagId=$tagId CREATE 入队失败(本地已保存)", e)
            }
        }
        return remoteId
    }

    /** [T12b/T14] PATCH 入队 + kick；PendingCreate 先确保 CREATE 入队，remoteId 暂用 clientUuid。 */
    private suspend fun pushTagPatch(current: TagCacheEntity, name: String? = null, colorHash: String? = null) {
        val remoteId = ensureTagCreateEnqueued(current.id) ?: return
        try {
            serverApi.patchTag(current.id, remoteId, name = name, colorHash = colorHash)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushTagPatch: tag=${current.id} 入队失败(本地已保存)", e)
        }
    }

    private suspend fun pushTagMember(tagId: Long, contactId: Long) {
        val tag = tagDao.getTagById(tagId) ?: return
        val tagUuid = tag.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addTagMember(tagId, tagUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushTagMember: add member 入队失败 tag=$tagUuid person=$personUuid", e)
        }
    }

    private suspend fun pushTagMemberRemove(tagId: Long, contactId: Long) {
        val tag = tagDao.getTagById(tagId) ?: return
        val tagUuid = tag.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.removeTagMember(tagId, tagUuid, personUuid)
        } catch (e: Exception) {
            BadgerLog.w(TAG, "pushTagMemberRemove: remove member 入队失败 tag=$tagUuid person=$personUuid", e)
        }
    }

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
