package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.UserProfileResponse

interface UserProfileRepository {

    fun getUserProfile(): Flow<UserProfileCacheEntity?>

    suspend fun getUserProfileOnce(): UserProfileCacheEntity?

    suspend fun saveUserProfile(profile: UserProfileCacheEntity)

    suspend fun updatePlatformField(
        fieldKey: String,
        jumpLink: String,
        value: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        originalLink: String? = null
    )

    suspend fun removePlatform(platformName: String)

    

    suspend fun editUserProfile(transform: (UserProfileCacheEntity) -> UserProfileCacheEntity): UserProfileCacheEntity

    

    suspend fun applyRemoteProfile(resp: UserProfileResponse)

    

    suspend fun applySyncedSelfPerson(person: PersonDto)

    

    suspend fun refreshFromServer(): Boolean
}
