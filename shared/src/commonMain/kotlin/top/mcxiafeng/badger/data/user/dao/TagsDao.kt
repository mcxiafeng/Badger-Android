package top.mcxiafeng.badger.data.user.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid
import top.mcxiafeng.badger.data.user.entity.Tags
import top.mcxiafeng.badger.data.user.entity.UserTagsRef

@Dao
interface TagsDao {

    @Upsert
    suspend fun upsertTags(tags: Tags)

    @Delete
    suspend fun deleteTags(tags: Tags)

    @Query("SELECT * FROM Tags")
    fun observeAllTags(): Flow<List<Tags>>

    @Upsert
    suspend fun upsertAll(tags: List<Tags>)

    @Query("DELETE FROM Tags WHERE uuid NOT IN (:keep)")
    suspend fun deleteTagsNotIn(keep: List<Uuid>)

    @Query("DELETE FROM Tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM Tags WHERE uuid = :uuid")
    suspend fun deleteTagsByUuid(uuid: Uuid)

    @Query("SELECT * FROM Tags WHERE uuid = :uuid")
    suspend fun getTags(uuid: Uuid): Tags?

    @Upsert
    suspend fun upsertUserTagsRef(ref: UserTagsRef)

    @Delete
    suspend fun deleteUserTagsRef(ref: UserTagsRef)

    @Query("SELECT Tags.* FROM Tags INNER JOIN UserTagsRef ON Tags.uuid = UserTagsRef.tagsUuid WHERE UserTagsRef.userUuid = :userUuid")
    suspend fun getTagsByUser(userUuid: Uuid): List<Tags>
}
