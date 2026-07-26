package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.ContactTagCacheEntity

/**
 * V2 联系人 ↔ 标签 多对多关联 DAO(对应表 `contact_tag_cache`)。
 *
 * [A3] 补全 `observeCrossRefsForContacts` Flow 批量查询,供 TagRepository.observeTagsForContacts 使用。
 */
@Dao
interface ContactTagCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: ContactTagCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<ContactTagCacheEntity>)

    @Query("DELETE FROM contact_tag_cache WHERE contactId = :contactId AND tagId = :tagId")
    suspend fun removeCrossRef(contactId: Long, tagId: Long)

    @Query("DELETE FROM contact_tag_cache WHERE contactId = :contactId")
    suspend fun clearContactTags(contactId: Long)

    /** [V2-P6] 关键操作 commitDelete:物理删除联系人时清掉 tag 关联。 */
    @Query("DELETE FROM contact_tag_cache WHERE contactId = :contactId")
    suspend fun clearByContact(contactId: Long)

    @Query("SELECT tagId FROM contact_tag_cache WHERE contactId = :contactId")
    suspend fun getTagIdsByContact(contactId: Long): List<Long>

    @Query("SELECT contactId FROM contact_tag_cache WHERE tagId = :tagId")
    suspend fun getContactIdsByTag(tagId: Long): List<Long>

    @Query("SELECT * FROM contact_tag_cache WHERE contactId IN (:contactIds)")
    suspend fun getCrossRefsForContacts(contactIds: List<Long>): List<ContactTagCacheEntity>

    @Query("SELECT * FROM contact_tag_cache WHERE contactId IN (:contactIds)")
    fun observeCrossRefsForContacts(contactIds: List<Long>): Flow<List<ContactTagCacheEntity>>

    @Query("DELETE FROM contact_tag_cache WHERE contactId = :contactId AND source = :source")
    suspend fun clearCrossRefsBySource(contactId: Long, source: String)

    @Query("SELECT tagId FROM contact_tag_cache WHERE contactId = :contactId")
    fun observeTagIdsByContact(contactId: Long): Flow<List<Long>>
}