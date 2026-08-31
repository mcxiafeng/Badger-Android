package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.TagCacheEntity

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

    @Query("SELECT * FROM tags_cache WHERE serverId = :serverId LIMIT 1")
    suspend fun getTagByServerId(serverId: String): TagCacheEntity?

    @Query("DELETE FROM tags_cache WHERE serverId = :serverId")
    suspend fun deleteTagByServerId(serverId: String)

    @Query("SELECT * FROM tags_cache WHERE id IN (:ids)")
    suspend fun searchTagsByIds(ids: List<Long>): List<TagCacheEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagOrIgnore(tag: TagCacheEntity): Long

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

    @Query("SELECT COUNT(*) FROM tags_cache")
    fun observeRowCount(): Flow<Int>
}
