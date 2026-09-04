package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity

/**
 * V2 用户资料 DAO（对应表 `user_profile_cache`，单例记录 id=1）。
 *
 * 与 V1 [top.mcxiafeng.badger.data.UserProfileDao] 1:1 对应。
 */
@Dao
interface UserProfileCacheDao {

    @Query("SELECT * FROM user_profile_cache WHERE id = 1")
    fun getProfile(): Flow<UserProfileCacheEntity?>

    @Query("SELECT * FROM user_profile_cache WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileCacheEntity)

    /** 触发 user_profile 表变更通知（同值覆盖也重发） */
    @Query("UPDATE user_profile_cache SET updateTime = updateTime WHERE id = 1")
    suspend fun bumpProfile()
}
