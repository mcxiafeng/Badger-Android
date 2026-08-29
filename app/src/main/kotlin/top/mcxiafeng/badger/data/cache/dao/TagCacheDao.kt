package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

/**
 * V2 标签 DAO(对应表 `tags_cache`)。
 *
 * [A3] 补全 `searchTagsByIds` 批量查询,供 TagRepository.getTagsByContact / getTagsForContactsOnce 使用。
 */
@Dao
interface TagCacheDao {

    @Query("SELECT * FROM tags_cache ORDER BY pinyinInitial ASC, name ASC")
    fun observeAllTags(): Flow<List<TagCacheEntity>>

    @Query("SELECT * FROM tags_cache ORDER BY pinyinInitial ASC, name ASC")
    suspend fun getAllTagsOnce(): List<TagCacheEntity>

    @Query("SELECT * FROM tags_cache WHERE id = :id LIMIT 1")
    suspend fun getTagById(id: Long): TagCacheEntity?

    @Query("SELECT * FROM tags_cache WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagCacheEntity?

    /** [Phase 3] 按服务端 uuid 查本地行（sync 重放定位）。 */
    @Query("SELECT * FROM tags_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getTagByServerId(serverId: String): TagCacheEntity?

    /** [Phase 3] 删除本地行（sync REMOVE 重放）。 */
    @Query("DELETE FROM tags_cache WHERE serverId = :serverId")
    suspend fun deleteTagByServerId(serverId: String)

    @Query("SELECT * FROM tags_cache WHERE id IN (:ids)")
    suspend fun searchTagsByIds(ids: List<Long>): List<TagCacheEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagCacheEntity): Long

    @Update
    suspend fun updateTag(tag: TagCacheEntity)

    @Query("UPDATE tags_cache SET name = :newName, pinyinInitial = :newPinyinInitial WHERE id = :id")
    suspend fun renameTag(id: Long, newName: String, newPinyinInitial: String)

    @Query("UPDATE tags_cache SET pinyinInitial = :pinyinInitial WHERE id = :id")
    suspend fun updatePinyinInitial(id: Long, pinyinInitial: String)

    @Query("UPDATE tags_cache SET showDot = :show WHERE id = :id")
    suspend fun setTagDotVisible(id: Long, show: Boolean)

    @Query("SELECT * FROM tags_cache WHERE name LIKE '%' || :query || '%' ORDER BY pinyinInitial ASC, name ASC LIMIT 30")
    suspend fun searchTagsByName(query: String): List<TagCacheEntity>

    @Query("DELETE FROM tags_cache WHERE id = :id")
    suspend fun deleteTagById(id: Long)

    /** [C1] Dashboard 标签计数。 */
    @Query("SELECT COUNT(*) FROM tags_cache")
    fun observeRowCount(): Flow<Int>
}