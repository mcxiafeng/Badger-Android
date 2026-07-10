package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactTagCrossRef
import top.mcxiafeng.badger.data.ContactTagDao
import top.mcxiafeng.badger.data.ContactTagJoin
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.TagDao
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签仓库实现。
 *
 * - 写关联后通过 [ContactDao.bumpContact] 触发 PagingSource/Flow 重发
 *   （参见 ContactRepositoryImpl.insertOne 注释解释的失效链路）。
 * - upsertTag 在同名已存在时返回现 id，避免 unique 索引冲突。
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val contactTagDao: ContactTagDao,
    private val contactDao: ContactDao,
) : TagRepository {

    // ========== 标签 CRUD ==========

    override fun observeAllTags(): Flow<List<Tag>> = tagDao.observeAllTags()

    override suspend fun getAllTagsOnce(): List<Tag> = withContext(Dispatchers.IO) {
        tagDao.getAllTagsOnce()
    }

    override suspend fun getTagById(id: Long): Tag? = withContext(Dispatchers.IO) {
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
        val tag = Tag(
            name = trimmed,
            color = color,
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
            source = source,
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

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        tagDao.deleteTagById(id)
    }

    // ========== 联系人 ↔ 标签 关联 ==========

    override fun observeTagsByContact(contactId: Long): Flow<List<Tag>> =
        contactTagDao.observeTagsByContact(contactId)

    override suspend fun getTagsByContact(contactId: Long): List<Tag> =
        withContext(Dispatchers.IO) {
            contactTagDao.getTagsByContactOnce(contactId)
        }

    override suspend fun getContactsByTag(tagId: Long): List<top.mcxiafeng.badger.data.Contact> =
        withContext(Dispatchers.IO) {
            val contactIds = contactTagDao.getContactIdsByTag(tagId)
            if (contactIds.isEmpty()) return@withContext emptyList()
            contactIds.mapNotNull { contactDao.getContactById(it) }
        }

    override suspend fun addTagToContact(contactId: Long, tagId: Long) = withContext(Dispatchers.IO) {
        contactTagDao.insertCrossRef(ContactTagCrossRef(contactId, tagId))
        bumpContact(contactId)
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>) =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext
            contactTagDao.insertCrossRefs(tagIds.map { ContactTagCrossRef(contactId, it) })
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

    override suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<Tag>> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyMap()
            val rows = contactTagDao.getTagsForContactsOnce(contactIds)
            rows.groupBy { it.contactId }.mapValues { entry ->
                entry.value.map(::joinToTag)
            }
        }

    override fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<Tag>>> =
        contactTagDao.observeTagsForContacts(contactIds).map { rows ->
            rows.groupBy { it.contactId }.mapValues { entry ->
                entry.value.map(::joinToTag)
            }
        }

    private fun joinToTag(j: ContactTagJoin): Tag = Tag(
        id = j.id,
        name = j.name,
        color = j.color,
        pinyinInitial = j.pinyinInitial,
        source = j.source,
        createTime = j.createTime,
    )

    /**
     * 触发 PagingSource/Flow 重发（参见 ContactDao.bumpContact 注释）。
     * 单个联系人一次性 bump 即可，无需对每个 tag 单独处理。
     */
    private suspend fun bumpContact(contactId: Long) {
        contactDao.bumpContact(contactId)
    }

    private companion object {
        const val TAG = "TagRepository"
    }
}
