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

    override suspend fun upsertTag(
        name: String,
        color: Long,
        source: String,
    ): Long = withContext(Dispatchers.IO) {
        val tagId = upsertTagLocal(name, color, source)
        syncTagCreate(tagId)
        tagId
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
    }

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        val uuid = current.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteTag(uuid)
            } catch (e: Exception) {
                Log.w(TAG, "deleteTag: remote delete failed; keep local tag id=$id", e)
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
        tagDao.updateTag(current.copy(color = color, colorHash = colorHash))
        val affectedContactIds = contactTagDao.getContactIdsByTag(id)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
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
            contactTagDao.insertCrossRefs(
                fromContactIds.map { ContactTagCacheEntity(contactId = it, tagId = toTagId) }
            )
            fromContactIds.forEach { contactDao.bumpContact(it) }
            fromContactIds.forEach { pushTagMember(toTagId, it) }
        }

        val uuid = fromTag.serverId?.takeIf { it.isNotBlank() }
        if (uuid != null) {
            try {
                serverApi.deleteTag(uuid)
            } catch (e: Exception) {
                Log.w(TAG, "reassignTagUsage: remote delete failed; keeping source tag id=$fromTagId", e)
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
                serverApi.deleteTag(uuid)
            } catch (e: Exception) {
                Log.w(TAG, "forceDeleteTag: remote delete failed; keep local tag id=$tagId", e)
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

    override suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity> =
        withContext(Dispatchers.IO) {
            val contactIds = contactTagDao.getContactIdsByTag(tagId)
            if (contactIds.isEmpty()) return@withContext emptyList()
            contactIds.mapNotNull { contactDao.getContactById(it) }
        }

    override suspend fun addTagToContact(contactId: Long, tagId: Long): Unit = withContext(Dispatchers.IO) {
        contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId, tagId))
        contactDao.bumpContact(contactId)
        pushTagMember(tagId, contactId)
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>): Unit =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext
            contactTagDao.insertCrossRefs(tagIds.map { ContactTagCacheEntity(contactId, it) })
            contactDao.bumpContact(contactId)
            tagIds.forEach { pushTagMember(it, contactId) }
        }

    override suspend fun removeTagFromContact(contactId: Long, tagId: Long): Unit =
        withContext(Dispatchers.IO) {
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
                val tagId = upsertTagLocal(
                    name = candidate.name,
                    color = 0xFF1976D2L,
                    source = source,
                )
                contactTagDao.insertCrossRef(
                    ContactTagCacheEntity(contactId = contactId, tagId = tagId)
                )
                tagId
            }
        }
        contactDao.bumpContact(contactId)
        tagIds.distinct().forEach { tagId ->
            syncTagCreate(tagId)
            pushTagMember(tagId, contactId)
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
            distinct.map { tagExport ->
                val tagId = upsertTagLocal(
                    name = tagExport.name,
                    color = tagExport.color,
                    source = tagExport.source,
                )
                contactTagDao.insertCrossRef(
                    ContactTagCacheEntity(
                        contactId = contactId,
                        tagId = tagId,
                        source = tagExport.source,
                        confidence = tagExport.confidence,
                        createTime = now,
                    )
                )
                tagId
            }
        }
        contactDao.bumpContact(contactId)
        tagIds.distinct().forEach { tagId ->
            syncTagCreate(tagId)
            pushTagMember(tagId, contactId)
        }
    }

    private suspend fun upsertTagLocal(
        name: String,
        color: Long,
        source: String,
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        tagDao.getTagByName(trimmed)?.let { return it.id }

        val tag = TagCacheEntity(
            name = trimmed,
            color = color,
            colorHash = colorToHash(color),
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
            source = source,
            createTime = System.currentTimeMillis(),
        )
        return tagDao.insertTag(tag)
    }

    private suspend fun syncTagCreate(tagId: Long) {
        val current = tagDao.getTagById(tagId) ?: return
        if (!current.serverId.isNullOrBlank() && !current.isLocalOnly) return
        try {
            val serverUuid = serverApi.createTag(
                name = current.name,
                colorHash = current.colorHash ?: colorToHash(current.color),
                personMembers = null,
            )
            tagDao.updateTag(current.copy(serverId = serverUuid, isLocalOnly = false))
        } catch (e: Exception) {
            Log.w(TAG, "syncTagCreate: tagId=$tagId create 失败,保留本地状态", e)
        }
    }

    private suspend fun pushTagPatch(
        current: TagCacheEntity,
        name: String? = null,
        colorHash: String? = null,
    ) {
        val uuid = current.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.patchTag(uuid, name = name, colorHash = colorHash)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagPatch: tag=$uuid 失败(本地已保存)", e)
        }
    }

    private suspend fun pushTagMember(tagId: Long, contactId: Long) {
        val tagUuid = tagDao.getTagById(tagId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.addTagMember(tagUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagMember: add member 失败 tag=$tagUuid person=$personUuid", e)
        }
    }

    private suspend fun pushTagMemberRemove(tagId: Long, contactId: Long) {
        val tagUuid = tagDao.getTagById(tagId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        val personUuid = contactDao.getContactById(contactId)?.serverId?.takeIf { it.isNotBlank() } ?: return
        try {
            serverApi.removeTagMember(tagUuid, personUuid)
        } catch (e: Exception) {
            Log.w(TAG, "pushTagMemberRemove: remove member 失败 tag=$tagUuid person=$personUuid", e)
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
