package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }`。
 */
class UserProfileRepositoryImpl(
    private val userProfileCacheDao: UserProfileCacheDao
) : UserProfileRepository {

    private val userProfileMutex = Mutex()

    override fun getUserProfile(): Flow<UserProfileCacheEntity?> = userProfileCacheDao.getProfile()

    override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = withContext(Dispatchers.IO) {
        userProfileCacheDao.getProfileOnce()
    }

    override suspend fun saveUserProfile(profile: UserProfileCacheEntity) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            userProfileCacheDao.saveProfile(profile)
            userProfileCacheDao.bumpProfile()
        }
    }

    override suspend fun updateAvatarPath(avatarPath: String?) = userProfileMutex.withLock {
                withContext(Dispatchers.IO) {
            val profile = userProfileCacheDao.getProfileOnce() ?: run {
                                return@withContext
            }
            userProfileCacheDao.saveProfile(profile.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis()))
            userProfileCacheDao.bumpProfile()
        }
    }

    override suspend fun updatePlatformField(
        fieldKey: String,
        jumpLink: String,
        value: String?,
        displayName: String?,
        avatarUrl: String?,
        originalLink: String?
    ) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileCacheDao.getProfileOnce()
                ?: UserProfileCacheEntity(name = "用户", updateTime = System.currentTimeMillis())
            val currentPlatforms = ContactMapper.decodePlatformsMap(profile.platformsJson)
            val newPlatforms = (currentPlatforms?.toMutableMap() ?: mutableMapOf()).apply {
                if (jumpLink.isBlank() && value.isNullOrBlank()) {
                    remove(fieldKey)
                } else {
                    this[fieldKey] = PlatformEntry(
                        displayName = displayName?.ifBlank { null },
                        jumpLink = jumpLink,
                        originalLink = originalLink?.ifBlank { null },
                        value = value?.ifBlank { null },
                        avatarUrl = avatarUrl?.ifBlank { null }
                    )
                }
            }
            val updated = profile.copy(
                platformsJson = ContactMapper.encodePlatformsMap(newPlatforms),
                updateTime = System.currentTimeMillis()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
        }
    }

    override suspend fun removePlatform(platformName: String) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileCacheDao.getProfileOnce() ?: return@withContext
            val currentPlatforms = ContactMapper.decodePlatformsMap(profile.platformsJson)
            val newPlatforms = currentPlatforms?.toMutableMap() ?: mutableMapOf()
            newPlatforms.remove(platformName)
            val updated = profile.copy(
                platformsJson = ContactMapper.encodePlatformsMap(newPlatforms),
                updateTime = System.currentTimeMillis()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
        }
    }
}