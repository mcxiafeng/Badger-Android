package top.mcxiafeng.badger.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.google.gson.JsonObject
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
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import top.mcxiafeng.badger.utils.PinyinUtils
import java.util.UUID

/**
 * 标签仓库实现。
 *
 * [A3] 全部走 V2 cache DAO,Tag 类型替换为 `TagCacheEntity`。
 *
 * [§14.2] Hilt `@Singleton @Inject constructor` → Koin `singleOf(::TagRepositoryImpl) { bind<TagRepository>() }`。
 *
 * [V2-P12] 标签的 CRUD 走 PendingUpload 队列(opType = TAG_UPSERT / TAG_DELETE),
 * 与 ContactRepository(P5) 风格一致。`color` / `name` / `pinyinInitial` 的变更全部归并为 TAG_UPSERT
 * (服务端按 name 去重,PATCH /v1/tags/{id} 改 name/color,pinyin_initial 由服务端重算)。
 */
class TagRepositoryImpl(
    private val tagDao: TagCacheDao,
    private val contactTagDao: ContactTagCacheDao,
    private val contactDao: ContactCacheDao,
    private val db: AppDatabase,
    // [V2-P12] 接 PendingUpload 队列
    private val pendingDao: PendingUploadDao,
    private val pendingUploadScheduler: PendingUploadScheduler,
    private val deviceIdProvider: DeviceIdProvider,
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
        // [V2-P12] 入队 op — id 传 0,Worker 走 POST /v1/tags 新建路径。
        enqueueTagUpsert(
            id = 0L,
            name = trimmed,
            color = color,
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
        )
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
        // [V2-P12] rename 也算 upsert(name 是 PATCH 的核心字段)。
        enqueueTagUpsert(
            id = id,
            name = trimmed,
            color = current.color,
            pinyinInitial = PinyinUtils.getContactPinyinInitial(trimmed),
        )
    }

    override suspend fun recomputePinyinInitial(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        val newPinyin = PinyinUtils.getContactPinyinInitial(current.name)
        tagDao.updatePinyinInitial(id, newPinyin)
        // [V2-P12] pinyin 重算通常由服务端重算(新建时),但客户端也存了 pinyinInitial — 入队让两端保持一致。
        enqueueTagUpsert(
            id = id,
            name = current.name,
            color = current.color,
            pinyinInitial = newPinyin,
        )
    }

    override suspend fun deleteTag(id: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        tagDao.deleteTagById(id)
        // [V2-P12] 入队 TAG_DELETE — payload 带 id 让服务端 DELETE /v1/tags/{id}。
        enqueueTagDelete(id = id, name = current.name)
    }

    override suspend fun setTagDotVisible(id: Long, show: Boolean) = withContext(Dispatchers.IO) {
        // [修复防御]: setTagDotVisible 仅改 dot_visible 字段,不入队列(服务端无需知道 dot 状态)。
        tagDao.setTagDotVisible(id, show)
    }

    override suspend fun setTagColor(id: Long, color: Long) = withContext(Dispatchers.IO) {
        val current = tagDao.getTagById(id) ?: return@withContext
        if (current.color == color) {
            Log.d(TAG, "setTagColor: tagId=$id no change, skip")
            return@withContext
        }
        tagDao.updateTag(current.copy(color = color))
        val affectedContactIds = contactTagDao.getContactIdsByTag(id)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        // [V2-P12] 颜色变化入队列(TAG_UPSERT 仅带 color 不带 name 也合法)。
        enqueueTagUpsert(
            id = id,
            name = current.name,
            color = color,
            pinyinInitial = current.pinyinInitial,
        )
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
        }
        val fromTag = tagDao.getTagById(fromTagId)
        tagDao.deleteTagById(fromTagId)
        // [V2-P12] reassign 后 fromTagId 被删 — 入队 TAG_DELETE。
        if (fromTag != null) {
            enqueueTagDelete(id = fromTagId, name = fromTag.name)
        }
        Log.d(TAG, "reassignTagUsage: from=$fromTagId to=$toTagId affectedContacts=${fromContactIds.size}")
    }

    override suspend fun forceDeleteTag(tagId: Long): List<Long> = withContext(Dispatchers.IO) {
        val affectedContactIds = contactTagDao.getContactIdsByTag(tagId)
        val current = tagDao.getTagById(tagId)
        tagDao.deleteTagById(tagId)
        affectedContactIds.forEach { contactDao.bumpContact(it) }
        // [V2-P12] force delete 也入队。
        if (current != null) {
            enqueueTagDelete(id = tagId, name = current.name)
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
    }

    override suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>): Unit =
        withContext(Dispatchers.IO) {
            if (tagIds.isEmpty()) return@withContext
            contactTagDao.insertCrossRefs(tagIds.map { ContactTagCacheEntity(contactId, it) })
            bumpContact(contactId)
        }

    override suspend fun removeTagFromContact(contactId: Long, tagId: Long): Unit =
        withContext(Dispatchers.IO) {
            contactTagDao.removeCrossRef(contactId, tagId)
            bumpContact(contactId)
        }

    override suspend fun clearContactTags(contactId: Long): Unit = withContext(Dispatchers.IO) {
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
        selected: List<top.mcxiafeng.badger.ai.AiTagGenerator.TagCandidate>,
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
    }

    override suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<top.mcxiafeng.badger.data.TagExport>,
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
    }

    private suspend fun bumpContact(contactId: Long) {
        contactDao.bumpContact(contactId)
    }

    // ========== [V2-P12] op 入队辅助 ==========

    private suspend fun enqueueTagUpsert(
        id: Long,
        name: String,
        color: Long,
        pinyinInitial: String,
    ) {
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // [修复防御]: 颜色 Long 0xAARRGGBB 存进 JSON 时用无符号 Long 格式,服务端接收时再按 hex 解析。
        val colorStr = "0x${color.toString(16).uppercase().padStart(8, '0')}"
        val payload = JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            addProperty("color", colorStr)
            addProperty("pinyin_initial", pinyinInitial)
        }
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = -1L,
                opType = OperationTypes.TAG_UPSERT,
                resourceVersion = 0L,
                payloadJson = payload.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        pendingUploadScheduler.kick()
        Log.d(TAG, "enqueueTagUpsert: opId=${opId.take(8)} id=$id name=$name color=$colorStr")
    }

    private suspend fun enqueueTagDelete(id: Long, name: String) {
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
        }
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = -1L,
                opType = OperationTypes.TAG_DELETE,
                resourceVersion = 0L,
                payloadJson = payload.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        pendingUploadScheduler.kick()
        Log.d(TAG, "enqueueTagDelete: opId=${opId.take(8)} id=$id name=$name")
    }

    private companion object {
        const val TAG = "TagRepository"
    }
}