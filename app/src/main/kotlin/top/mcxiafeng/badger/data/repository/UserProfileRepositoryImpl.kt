package top.mcxiafeng.badger.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.UserProfile
import top.mcxiafeng.badger.data.UserProfileDao
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao
) : UserProfileRepository {

    private val userProfileMutex = Mutex()

    override fun getUserProfile(): Flow<UserProfile?> = userProfileDao.getProfile()

    override suspend fun getUserProfileOnce(): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getProfileOnce()
    }

    override suspend fun saveUserProfile(profile: UserProfile) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            userProfileDao.saveProfile(profile)
            // 兜底再触发一次 Flow 重发，处理"同值被覆盖不通知下游"问题。
            userProfileDao.bumpProfile()
        }
    }

    override suspend fun updateAvatarPath(avatarPath: String?) = userProfileMutex.withLock {
        Log.d("Tester", "updateAvatarPath: avatarPath=$avatarPath")
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: run {
                Log.d("Tester", "updateAvatarPath: profile is null, skipping")
                return@withContext
            }
            userProfileDao.saveProfile(profile.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis()))
            userProfileDao.bumpProfile()
        }
    }

    override suspend fun updateCardImagePath(cardImagePath: String?) = userProfileMutex.withLock {
        Log.d("Tester", "updateCardImagePath: cardImagePath=$cardImagePath")
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: run {
                Log.d("Tester", "updateCardImagePath: profile is null, skipping")
                return@withContext
            }
            userProfileDao.saveProfile(profile.copy(cardImagePath = cardImagePath, updateTime = System.currentTimeMillis()))
            userProfileDao.bumpProfile()
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
            val profile = userProfileDao.getProfileOnce()
                ?: UserProfile(name = "用户", updateTime = System.currentTimeMillis())
            val newPlatforms = (profile.platforms?.toMutableMap() ?: mutableMapOf()).apply {
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
            val updated = profile.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            userProfileDao.saveProfile(updated)
            userProfileDao.bumpProfile()
        }
    }

    override suspend fun removePlatform(platformName: String) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileDao.getProfileOnce() ?: return@withContext
            val newPlatforms = profile.platforms?.toMutableMap() ?: mutableMapOf()
            newPlatforms.remove(platformName)
            val updated = profile.copy(platforms = newPlatforms, updateTime = System.currentTimeMillis())
            userProfileDao.saveProfile(updated)
            userProfileDao.bumpProfile()
        }
    }
}
