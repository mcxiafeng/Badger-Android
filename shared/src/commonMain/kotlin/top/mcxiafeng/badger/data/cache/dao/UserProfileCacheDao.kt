package top.mcxiafeng.badger.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity

@Dao
interface UserProfileCacheDao {

    @Query("SELECT * FROM user_profile_cache WHERE id = 1")
    fun getProfile(): Flow<UserProfileCacheEntity?>

    @Query("SELECT * FROM user_profile_cache WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileCacheEntity)

    
    @Query("UPDATE user_profile_cache SET updateTime = updateTime WHERE id = 1")
    suspend fun bumpProfile()

    
    @Query("DELETE FROM user_profile_cache")
    suspend fun clearAll()
}
