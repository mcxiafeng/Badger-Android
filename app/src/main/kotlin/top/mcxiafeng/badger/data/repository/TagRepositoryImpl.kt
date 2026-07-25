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
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签仓库实现。
 *
 * [A3] 全部走 V2 cache DAO,Tag 类型替换为 `TagCacheEntity`。
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagCacheDao,
    private val contactTagDao: ContactTagCacheDao,
    private val contactDao: ContactCacheDao,
    private val db: AppDatabase,
) : TagRepository {

    // ========== 标签 CRUD ==========

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
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        tagDao.getTagByName(trimmed)?.let { existing ->
            Log.d(TAG, "upsertTag: reuse existing tag id=${existing.id} name='$trimmed'")
            return@withContext existing.id
        }
        val tag = TagCacheEntity(
            name = trimmed,
            color = color,
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
            source = source,
            createTime = System.currentTimeMillis(),
        )
        val newId = tagDao.insertTag(tag)
        Log.d(TAG, "upsertTag: created new tag id=$newId name='$trimmed' source=$source")
        newId
    }

    override suspend fun renameTag(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        tagDao.renameTag(
            id = id,
            newName = trimmed,
            newPinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
        )
    }

    override suspend fun recomputePinyinInitial(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.updatePinyinInitial(id, PinyinUtils.getContactPinyinInitial(current.name))
    }

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        tagDao.deleteTagById(id)
    }

    override suspend fun setTagDotVisible(id: Long, show: Boolean) = withContext(Dispatchers.IO) {
        tagDao.setTagDotVisible(id, show)
    }

    override suspend fun setTagColor(id: Long, color: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.updateTag(current.copy(color = color))
        val affectedContactIds = contactTagDao.getContactIdsByTag(id)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        Log.d(TAG, "setTagColor: tagId=$id color=0x${color.toString(16)} affected=${affectedContactIds.size}")
    }

    override suspend fun searchTagsByName(query: String): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        tagDao.searchTagsByName(query)
    }

    override suspend fun searchTagsFts(query: String, limit: Int): List<TagCacheEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // [A3] FTS 已弃用,走 LIKE 路径
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
        }
        tagDao.deleteTagById(fromTagId)
        Log.d(TAG, "reassignTagUsage: from=$fromTagId to=$toTagId affectedContacts=${fromContactIds.size}")
    }

    override suspend fun forceDeleteTag(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val affectedContactIds = contactTagDao.getContactIdsByTag(tagId)
        tagDao.deleteTagById(tagId)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
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

    override suspend fun addTagToContact(contactId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        contactTagDao.insertCrossRef(ContactTagCacheEntity(contactId, tagId))
        bumpContact(contactId)
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>) =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext
            contactTagDao.insertCrossRefs(tagIds.map { ContactTagCacheEntity(contactId, it) })
            bumpContact(contactId)
        }

    override suspend fun removeTagFromContact(contactId: Long, tagId: Long) =
        withContext(Dispatchers.IO) {
            contactTagDao.removeCrossRef(contactId, tagId)
            bumpContact(contactId)
        }

    override suspend fun clearContactTags(contactId: Long) = withContext(Dispatchers.IO) {
        contactTagDao.clearContactTags(contactId)
        bumpContact(contactId)
    }

    override suspend fun clearContactTagsBySource(contactId: Long, source: String): Int = withContext(Dispatchers.IO) {
        contactTagDao.clearCrossRefsBySource(contactId, source)
        bumpContact(contactId)
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
            val tags = if (tagIds.isEmpty()) emptyMap() else tagDao.searchTagsByIds(tagIds).associateBy { it.id }
            refs.groupBy { it.contactId }.mapValues { entry ->
                entry.value.mapNotNull { tags[it.tagId] }
            }
        }
    }

    private suspend fun bumpContact(contactId: Long) {
        contactDao.bumpContact(contactId)
    }

    override suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<AiTagGenerator.TagCandidate>,
        source: String,
    ) = withContext(Dispatchers.IO) {
        val deduped = selected.distinctBy { it.name.trim() }.filter { it.name.isNotBlank() }
        if (deduped.isEmpty()) {
            Log.d(TAG, "applyAiTagCandidatesAtomic: empty after dedup, skip")
            return@withContext
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val refs = deduped.map { c ->
                val tagId = if (c.matchedExisting && c.existingTagId != null) {
                    c.existingTagId
                } else {
                    upsertTagInternal(c.name, c.color, source = "ai")
                }
                ContactTagCacheEntity(
                    contactId = contactId,
                    tagId = tagId,
                    source = source,
                    confidence = c.confidence.takeIf { it in 0f..1f } ?: 1.0f,
                    createTime = now,
                )
            }
            contactTagDao.insertCrossRefs(refs)
        }
        bumpContact(contactId)
        Log.d(TAG, "applyAiTagCandidatesAtomic: contact=$contactId applied=${deduped.size}")
    }

    private suspend fun upsertTagInternal(name: String, color: Long, source: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        val existing = tagDao.getTagByName(trimmed)
        return if (existing != null) {
            existing.id
        } else {
            val tag = TagCacheEntity(
                name = trimmed,
                color = color,
                pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
                source = source,
                createTime = System.currentTimeMillis(),
            )
            tagDao.insertTag(tag)
        }
    }

    override suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<TagExport>,
        now: Long,
    ) = withContext(Dispatchers.IO) {
        if (tagExports.isEmpty()) return@withContext
        val deduped = tagExports.distinctBy { it.name.trim() }.filter { it.name.isNotBlank() }
        if (deduped.isEmpty()) return@withContext
        db.withTransaction {
            val refs = deduped.map { te ->
                val existing = tagDao.getTagByName(te.name.trim())
                val tagId = if (existing != null) {
                    if (existing.color != te.color) {
                        try {
                            tagDao.updateTag(existing.copy(color = te.color))
                        } catch (e: Exception) {
                            Log.w(TAG, "applyImportedTags: color update failed for id=${existing.id}", e)
                        }
                    }
                    existing.id
                } else {
                    val tag = TagCacheEntity(
                        name = te.name.trim(),
                        color = te.color,
                        pinyinInitial = PinyinUtils.getContactPinyinInitial(te.name.trim()),
                        source = te.source.ifBlank { "import" },
                        createTime = System.currentTimeMillis(),
                    )
                    tagDao.insertTag(tag)
                }
                ContactTagCacheEntity(
                    contactId = contactId,
                    tagId = tagId,
                    source = te.source.ifBlank { "import" },
                    confidence = te.confidence.takeIf { it in 0f..1f } ?: 1.0f,
                    createTime = te.createTime.takeIf { it > 0 } ?: now,
                )
            }
            contactTagDao.insertCrossRefs(refs)
        }
    }

    private companion object {
        const val TAG = "TagRepository"
    }
}