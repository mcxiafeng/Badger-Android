package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity

/**
 * 用户资料数据仓库接口。
 *
 * [A3] 输出 V2 cache entity (`UserProfileCacheEntity`)。
 */
interface UserProfileRepository {

    fun getUserProfile(): Flow<UserProfileCacheEntity?>

    suspend fun getUserProfileOnce(): UserProfileCacheEntity?

    suspend fun saveUserProfile(profile: UserProfileCacheEntity)

    suspend fun updateAvatarPath(avatarPath: String?)

    suspend fun updatePlatformField(
        fieldKey: String,
        jumpLink: String,
        value: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        originalLink: String? = null
    )

    suspend fun removePlatform(platformName: String)
}