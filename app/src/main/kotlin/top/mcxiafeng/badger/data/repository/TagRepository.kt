package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

/**
 * 标签数据仓库接口。
 *
 * [A3] 全部输出 V2 cache entity(`TagCacheEntity` / `ContactCacheEntity` / `ContactTagCacheEntity`)。
 */
interface TagRepository {

    // ========== 标签 CRUD ==========

    /** 观察所有标签(按 pinyinInitial + name 排序) */
    fun observeAllTags(): Flow<List<TagCacheEntity>>

    /** 一次性获取所有标签 */
    suspend fun getAllTagsOnce(): List<TagCacheEntity>

    suspend fun getTagById(id: Long): TagCacheEntity?

    /** 同名复用:返回现存 tag 的 id;不存在则插入并返回新 id */
    suspend fun upsertTag(name: String, color: Long = 0xFF1976D2L, source: String = "manual"): Long

    suspend fun renameTag(id: Long, newName: String)

    /**
     * 仅重算 pinyinInitial(name 不变)。用于遗留 tag 一次性补齐。
     */
    suspend fun recomputePinyinInitial(id: Long)

    suspend fun deleteTag(id: Long)

    /** 设置标签列表项右侧色点显示开关 */
    suspend fun setTagDotVisible(id: Long, show: Boolean)

    /** 设置标签颜色(0xAARRGGBB Long)。改完后通过 [bumpContactForTags] 触发列表刷新。 */
    suspend fun setTagColor(id: Long, color: Long)

    /**
     * 按名字模糊搜索标签(用于 PersonPage 搜索"按标签命中")。
     * LIKE '%...%' 全表扫描;标签表通常 < 200 条,性能可接受。
     */
    suspend fun searchTagsByName(query: String): List<TagCacheEntity>

    /**
     * 把 fromTag 的所有联系人 cross-ref 转移到 toTag,然后删除 fromTag。
     */
    suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long)

    /**
     * 强制删除标签,返回被影响的联系人 ID 集合(供 UI 提示)。
     */
    suspend fun forceDeleteTag(tagId: Long): List<Long>

    // ========== 联系人 ↔ 标签 关联 ==========

    fun observeTagsByContact(contactId: Long): Flow<List<TagCacheEntity>>

    suspend fun getTagsByContact(contactId: Long): List<TagCacheEntity>

    suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity>

    suspend fun addTagToContact(contactId: Long, tagId: Long)

    suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>)

    suspend fun removeTagFromContact(contactId: Long, tagId: Long)

    suspend fun clearContactTags(contactId: Long)

    /** 按 source 清空某联系人的关联行(如"清空某联系人的所有 AI 标签") */
    suspend fun clearContactTagsBySource(contactId: Long, source: String): Int

    /**
     * 一次性拿所有联系人的标签,组装成 Map<contactId, List<TagCacheEntity>>。
     */
    suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<TagCacheEntity>>

    /** 批量拉取 (contactId, tagId) 关联行(含 source/confidence/createTime) */
    suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCacheEntity>

    /**
     * Flow 版批量拉取。
     */
    fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<TagCacheEntity>>>

    /**
     * 事务版 AI 候选标签采纳:distinctBy 去重 → 整批包在一个事务里,
     * 任一失败 → 整批回滚。
     */
    suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<top.mcxiafeng.badger.ai.AiTagGenerator.TagCandidate>,
        source: String = "ai"
    )

    /**
     * 事务版导入标签。
     */
    suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<top.mcxiafeng.badger.data.TagExport>,
        now: Long = System.currentTimeMillis()
    )
}