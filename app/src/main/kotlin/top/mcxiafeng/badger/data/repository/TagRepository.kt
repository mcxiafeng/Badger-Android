package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.Tag

/**
 * 标签数据仓库接口
 *
 * 设计要点：
 * - 标签名通过 unique 索引去重。upsertTag(name) 在已有同名时返回旧 id，新建时插入。
 * - 写关联后调用 [bumpContactForTags] 让 PagingSource 失效（与 ContactDao.bumpContact 同模式）。
 * - 详情页 → 联系人列表 项的标签 chip 通过 [observeTagsForContacts] 一次拿全，避免 N+1。
 */
interface TagRepository {

    // ========== 标签 CRUD ==========

    /** 观察所有标签（按 pinyinInitial + name 排序） */
    fun observeAllTags(): Flow<List<Tag>>

    /** 一次性获取所有标签 */
    suspend fun getAllTagsOnce(): List<Tag>

    suspend fun getTagById(id: Long): Tag?

    /** 同名复用：返回现存 tag 的 id；不存在则插入并返回新 id */
    suspend fun upsertTag(name: String, color: Long = 0xFF1976D2L, source: String = "manual"): Long

    suspend fun renameTag(id: Long, newName: String)

    suspend fun deleteTag(id: Long)

    // ========== 联系人 ↔ 标签 关联 ==========

    fun observeTagsByContact(contactId: Long): Flow<List<Tag>>

    suspend fun getTagsByContact(contactId: Long): List<Tag>

    suspend fun getContactsByTag(tagId: Long): List<Contact>

    suspend fun addTagToContact(contactId: Long, tagId: Long)

    suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>)

    suspend fun removeTagFromContact(contactId: Long, tagId: Long)

    suspend fun clearContactTags(contactId: Long)

    /**
     * 一次性拿所有联系人的标签，组装成 Map<contactId, List<Tag>>。
     * 用于 PersonPage 列表渲染 chip，避免对每条 Contact 单查。
     */
    suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<Tag>>

    /**
     * Flow 版批量拉取。和上面同样目的，但用 Flow 让 Room 自动失效。
     */
    fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<Tag>>>
}
