package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.ai.AiTagGenerator
import top.mcxiafeng.badger.data.Contact
import top.mcxiafeng.badger.data.ContactTagCrossRef
import top.mcxiafeng.badger.data.Tag
import top.mcxiafeng.badger.data.TagExport

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

    /**
     * 仅重算 pinyinInitial（name 不变）。用于遗留 tag 一次性补齐。
     */
    suspend fun recomputePinyinInitial(id: Long)

    suspend fun deleteTag(id: Long)

    /** 设置标签列表项右侧色点显示开关 */
    suspend fun setTagDotVisible(id: Long, show: Boolean)

    /** 设置标签颜色（0xAARRGGBB Long）。改完后通过 [bumpContactForTags] 触发列表刷新。 */
    suspend fun setTagColor(id: Long, color: Long)

    /**
     * 按名字模糊搜索标签（用于 PersonPage 搜索"按标签命中"）。
     * LIKE '%...%' 全表扫描；标签表通常 < 200 条，性能可接受。
     */
    suspend fun searchTagsByName(query: String): List<Tag>

    /** FTS4 搜索（FTS 命中为空时退化为 LIKE,见 [searchTagsByName]） */
    suspend fun searchTagsFts(query: String, limit: Int = 30): List<Tag>

    /**
     * 把 fromTag 的所有联系人 cross-ref 转移到 toTag,然后删除 fromTag。
     * 用于 TagManagerDialog 的"合并到其他标签"操作。
     */
    suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long)

    /**
     * 强制删除标签,返回被影响的联系人 ID 集合(供 UI 提示)。
     * cross-ref 由 FK CASCADE 自动清理。
     */
    suspend fun forceDeleteTag(tagId: Long): List<Long>

    // ========== 联系人 ↔ 标签 关联 ==========

    fun observeTagsByContact(contactId: Long): Flow<List<Tag>>

    suspend fun getTagsByContact(contactId: Long): List<Tag>

    suspend fun getContactsByTag(tagId: Long): List<Contact>

    suspend fun addTagToContact(contactId: Long, tagId: Long)

    suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>)

    suspend fun removeTagFromContact(contactId: Long, tagId: Long)

    suspend fun clearContactTags(contactId: Long)

    /** 按 source 清空某联系人的关联行（如"清空某联系人的所有 AI 标签"） */
    suspend fun clearContactTagsBySource(contactId: Long, source: String): Int

    /**
     * 一次性拿所有联系人的标签，组装成 Map<contactId, List<Tag>>。
     * 用于 PersonPage 列表渲染 chip，避免对每条 Contact 单查。
     */
    suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<Tag>>

    /** 批量拉取 (contactId, tagId) 关联行(含 source/confidence/createTime) */
    suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCrossRef>

    /**
     * Flow 版批量拉取。和上面同样目的，但用 Flow 让 Room 自动失效。
     */
    fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<Tag>>>

    /**
     * [P1-5] 事务版 AI 候选标签采纳：distinctBy 去重 → 整批包在一个事务里，
     * 任一失败 → 整批回滚，避免半写入 + 撞 unique 索引 ABORT。
     */
    suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<AiTagGenerator.TagCandidate>,
        source: String = "ai"
    )

    /**
     * [P1-9] 事务版导入标签：每个 tagExport 同名复用 → upsertTag(source 跟随)
     * → 批量 insertCrossRefs(source/confidence/createTime 跟随)。
     */
    suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<TagExport>,
        now: Long = System.currentTimeMillis()
    )
}
