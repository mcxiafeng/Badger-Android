package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.UserProfile

/**
 * 用户资料数据仓库接口
 *
 * 管理用户个人资料（我的名片）的操作。
 */
interface UserProfileRepository {

    fun getUserProfile(): Flow<UserProfile?>

    suspend fun getUserProfileOnce(): UserProfile?

    suspend fun saveUserProfile(profile: UserProfile)

    suspend fun updateAvatarPath(avatarPath: String?)

    suspend fun updateCardImagePath(cardImagePath: String?)

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
