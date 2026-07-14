package top.mcxiafeng.badger.data.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.ContactDao
import top.mcxiafeng.badger.data.ContactTagCrossRef
import top.mcxiafeng.badger.data.ContactTagDao
import top.mcxiafeng.badger.data.ContactTagJoin
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.TagDao
import top.mcxiafeng.badger.data.TagExport
import top.mcxiafeng.badger.data.TagFtsDao
import top.mcxiafeng.badger.utils.PinyinUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签仓库实现。
 *
 * - 写关联后通过 [ContactDao.bumpContact] 触发 PagingSource/Flow 重发
 *   （参见 ContactRepositoryImpl.insertOne 注释解释的失效链路）。
 * - upsertTag 在同名已存在时返回现 id，避免 unique 索引冲突。
 * - [applyAiTagCandidatesAtomic] / [applyImportedTags] 用 [AppDatabase.withTransaction]
 *   包整个写入过程,任一失败整批回滚。
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val contactTagDao: ContactTagDao,
    private val tagFtsDao: TagFtsDao,
    private val contactDao: ContactDao,
    private val db: AppDatabase,
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
        // [修复防御]: 颜色变更影响列表项右侧色点展示,需要让关联此 tag 的联系人触发 PagingSource/Flow 重发。
        val affectedContactIds = contactTagDao.getContactIdsByTag(id)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        Log.d(TAG, "setTagColor: tagId=$id color=0x${color.toString(16)} affected=${affectedContactIds.size}")
    }

    override suspend fun searchTagsByName(query: String): List<Tag> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        tagDao.searchTagsByName(query)
    }

    override suspend fun searchTagsFts(query: String, limit: Int): List<Tag> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // FTS4 MATCH 语法:用空格分词 + 加 * 表示前缀匹配
        val ftsQuery = query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
        if (ftsQuery.isBlank()) return@withContext emptyList()
        tagFtsDao.searchTagsFtsProjected(ftsQuery, limit).map { row ->
            Tag(
                id = row.id,
                name = row.name,
                color = row.color,
                pinyinInitial = row.pinyinInitial,
                source = row.source,
                showDot = row.showDot,
                createTime = row.createTime,
            )
        }
    }

    override suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long): Unit = withContext(Dispatchers.IO) {
        require(fromTagId != toTagId) { "fromTagId and toTagId must differ" }
        val fromContactIds = contactTagDao.getContactIdsByTag(fromTagId)
        if (fromContactIds.isNotEmpty()) {
            // 复用 REPLACE:忽略 conflict(联系人已经关联 toTag 的 cross-ref)
            contactTagDao.insertCrossRefs(
                fromContactIds.map { ContactTagCrossRef(contactId = it, tagId = toTagId) }
            )
            // 单独 bump 每个受影响的联系人,触发 PagingSource/Flow 重发
            fromContactIds.forEach { contactDao.bumpContact(it) }
        }
        // 删 fromTag(关联行由 CASCADE 自动清理)
        tagDao.deleteTagById(fromTagId)
        Log.d(TAG, "reassignTagUsage: from=$fromTagId to=$toTagId affectedContacts=${fromContactIds.size}")
    }

    override suspend fun forceDeleteTag(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val affectedContactIds = contactTagDao.getContactIdsByTag(tagId)
        tagDao.deleteTagById(tagId)
        // 单独 bump 每个受影响的联系人(虽然 cross_ref 被 CASCADE,但联系人自身也需要刷新)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        Log.d(TAG, "forceDeleteTag: tagId=$tagId affectedContacts=${affectedContactIds.size}")
        affectedContactIds
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

    override suspend fun clearContactTagsBySource(contactId: Long, source: String) = withContext(Dispatchers.IO) {
        contactTagDao.clearCrossRefsBySource(contactId, source)
        bumpContact(contactId)
        Log.d(TAG, "clearContactTagsBySource: contact=$contactId source=$source")
    }

    override suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCrossRef> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyList()
            contactTagDao.getCrossRefsForContacts(contactIds)
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
        showDot = j.showDot,
        createTime = j.createTime,
    )

    /**
     * 触发 PagingSource/Flow 重发（参见 ContactDao.bumpContact 注释）。
     * 单个联系人一次性 bump 即可，无需对每个 tag 单独处理。
     */
    private suspend fun bumpContact(contactId: Long) {
        contactDao.bumpContact(contactId)
    }

    /**
     * [P1-5] 原子采纳 AI 候选标签：先 distinctBy 去重，整批包在事务内，
     * 任一失败 → 整批回滚，避免半写入 + 撞 unique 索引 ABORT。
     *
     * @param contactId 目标联系人
     * @param selected AI 预览对话框中用户勾选的候选标签
     * @param source 写入 contact_tag.source 的值（默认 ai）
     */
    override suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<AiTagGenerator.TagCandidate>,
        source: String,
    ) = withContext(Dispatchers.IO) {
        // [P1-5] 先去重：同名 candidate 多次勾选只算一次
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
                    // upsertTag 内部已处理同名复用;AI 标签统一 source="ai"
                    upsertTagInternal(c.name, c.color, source = "ai")
                }
                ContactTagCrossRef(
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

    /**
     * [P1-5 / P1-9] 事务内 upsertTag（避免在事务外调用 withContext(Dispatchers.IO) 嵌套）。
     * 同名 tag 不重复创建,返回旧 id;source 跟随传入。
     */
    private suspend fun upsertTagInternal(name: String, color: Long, source: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "tag name must not be blank" }
        val existing = tagDao.getTagByName(trimmed)
        return if (existing != null) {
            Log.d(TAG, "upsertTagInternal: reuse existing tag id=${existing.id} name='$trimmed'")
            existing.id
        } else {
            val tag = Tag(
                name = trimmed,
                color = color,
                pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
                source = source,
            )
            tagDao.insertTag(tag).also { newId ->
                Log.d(TAG, "upsertTagInternal: created id=$newId name='$trimmed' source=$source")
            }
        }
    }

    /**
     * [P1-9] 把导入的 tags 列表应用到指定联系人。
     * - 同名已存在的 tag：保持现有 source (用户手动改的 source 不能被导入覆盖);
     *   但 confidence/createTime 用导入的新值（因为是新建关联）。
     * - 整批包在一个事务里,任一失败回滚。
     */
    override suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<TagExport>,
        now: Long,
    ) = withContext(Dispatchers.IO) {
        if (tagExports.isEmpty()) return@withContext
        // 同名 tagExport 只写一次关联
        val deduped = tagExports.distinctBy { it.name.trim() }.filter { it.name.isNotBlank() }
        if (deduped.isEmpty()) return@withContext
        db.withTransaction {
            val refs = deduped.map { te ->
                val existing = tagDao.getTagByName(te.name.trim())
                val tagId = if (existing != null) {
                    // 同名已存在:不覆盖已有 source（保留用户手动修改）;
                    // color 如果不同,更新（用户期待"无损还原"）。
                    if (existing.color != te.color) {
                        try {
                            tagDao.updateTag(existing.copy(color = te.color))
                            Log.d(TAG, "applyImportedTags: kept existing id=${existing.id}, color synced")
                        } catch (e: Exception) {
                            Log.w(TAG, "applyImportedTags: color update failed for id=${existing.id}", e)
                        }
                    }
                    existing.id
                } else {
                    // 新建:source 跟随导入 JSON
                    val tag = Tag(
                        name = te.name.trim(),
                        color = te.color,
                        pinyinInitial = PinyinUtils.getContactPinyinInitial(te.name.trim()),
                        source = te.source.ifBlank { "import" },
                    )
                    tagDao.insertTag(tag)
                }
                ContactTagCrossRef(
                    contactId = contactId,
                    tagId = tagId,
                    source = te.source.ifBlank { "import" },
                    confidence = te.confidence.takeIf { it in 0f..1f } ?: 1.0f,
                    createTime = te.createTime.takeIf { it > 0 } ?: now,
                )
            }
            contactTagDao.insertCrossRefs(refs)
        }
        bumpContact(contactId)
        Log.d(TAG, "applyImportedTags: contact=$contactId applied=${deduped.size}")
    }

    private companion object {
        const val TAG = "TagRepository"
    }
}
