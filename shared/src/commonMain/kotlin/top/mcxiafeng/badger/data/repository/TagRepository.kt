package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactCacheEntity
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity
import top.mcxiafeng.badger.shared.util.nowMs

interface TagRepository {

    

    
    fun observeAllTags(): Flow<List<TagCacheEntity>>

    
    suspend fun getAllTagsOnce(): List<TagCacheEntity>

    suspend fun getTagById(id: Long): TagCacheEntity?

    
    suspend fun upsertTag(name: String, color: Long = 0xFF1976D2L, source: String = "manual"): Long

    suspend fun renameTag(id: Long, newName: String)

    

    suspend fun recomputePinyinInitial(id: Long)

    suspend fun deleteTag(id: Long)

    
    suspend fun setTagDotVisible(id: Long, show: Boolean)

    
    suspend fun setTagColor(id: Long, color: Long)

    

    suspend fun searchTagsByName(query: String): List<TagCacheEntity>

    

    suspend fun reassignTagUsage(fromTagId: Long, toTagId: Long)

    

    suspend fun forceDeleteTag(tagId: Long): List<Long>

    

    fun observeTagsByContact(contactId: Long): Flow<List<TagCacheEntity>>

    suspend fun getTagsByContact(contactId: Long): List<TagCacheEntity>

    suspend fun getContactsByTag(tagId: Long): List<ContactCacheEntity>

    suspend fun addTagToContact(contactId: Long, tagId: Long)

    suspend fun addTagsToContact(contactId: Long, tagIds: List<Long>)

    suspend fun removeTagFromContact(contactId: Long, tagId: Long)

    suspend fun clearContactTags(contactId: Long)

    
    suspend fun clearContactTagsBySource(contactId: Long, source: String): Int

    

    suspend fun getTagsForContactsOnce(contactIds: List<Long>): Map<Long, List<TagCacheEntity>>

    
    suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCacheEntity>

    

    fun observeTagsForContacts(contactIds: List<Long>): Flow<Map<Long, List<TagCacheEntity>>>

    

    suspend fun applyAiTagCandidatesAtomic(
        contactId: Long,
        selected: List<top.mcxiafeng.badger.ai.AiTagGenerator.TagCandidate>,
        source: String = "ai"
    )

    

    suspend fun applyImportedTags(
        contactId: Long,
        tagExports: List<top.mcxiafeng.badger.data.importer.TagExport>,
        now: Long = nowMs()
    )
}
