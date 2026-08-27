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
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }`。
 *
 * [Phase 3] 直推改造：写操作（saveUserProfile / updateAvatarPath / updatePlatformField /
 * removePlatform）本地落 `user_profile_cache` 后**直推** `PUT /api/user/profile`
 * （`{ name?, profile? }`，嵌套 Profile 对象），不再走 PendingUpload 队列。
 *
 * 推失败**不阻塞本地保存**（本地是最终一致源，服务端下次 sync 以权威为准），但**必须打日志**——
 * 有观测的降级，不是静默吞错。
 *
 * 写前重读 + diff 防抖：与旧实现同模式，仅在实际字段变化时才推送。
 */
class UserProfileRepositoryImpl(
    private val userProfileCacheDao: UserProfileCacheDao,
    private val serverApi: ServerApi,
) : UserProfileRepository {

    private val userProfileMutex = Mutex()

    override fun getUserProfile(): Flow<UserProfileCacheEntity?> = userProfileCacheDao.getProfile()

    override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = withContext(Dispatchers.IO) {
        userProfileCacheDao.getProfileOnce()
    }

    /**
     * 全量 save（ProfileDetailPage 的"保存"按钮直接调）。
     */
    override suspend fun saveUserProfile(profile: UserProfileCacheEntity): Unit = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = userProfileCacheDao.getProfileOnce()
            userProfileCacheDao.saveProfile(profile)
            userProfileCacheDao.bumpProfile()
            val dirty = existing == null
                || existing.name != profile.name
                || existing.bio != profile.bio
                || existing.platformsJson != profile.platformsJson
                || existing.avatarPath != profile.avatarPath
                || existing.sex != profile.sex
                || existing.country != profile.country
                || existing.region != profile.region
                || existing.birthday != profile.birthday
                || existing.backgroundURL != profile.backgroundURL
                || existing.extra != profile.extra
            if (dirty) {
                pushProfile(name = profile.name, profile = buildProfileDto(profile))
            } else {
                Log.d(TAG, "saveUserProfile: 无变化,跳过推送")
            }
        }
    }

    override suspend fun updateAvatarPath(avatarPath: String?): Unit = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileCacheDao.getProfileOnce() ?: run {
                Log.w(TAG, "updateAvatarPath: profile not initialized, skip")
                return@withContext
            }
            if (profile.avatarPath == avatarPath) {
                Log.d(TAG, "updateAvatarPath: no change, skip")
                return@withContext
            }
            // [修复防御]: 即使是"清空头像"也要推（avatarURL=null），否则远端永远显示老头像。
            val updated = profile.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis())
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            pushProfile(name = null, profile = buildProfileDto(updated))
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
            pushProfile(name = null, profile = buildProfileDto(updated))
        }
    }

    override suspend fun removePlatform(platformName: String) = userProfileMutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = userProfileCacheDao.getProfileOnce() ?: return@withContext
            val currentPlatforms = ContactMapper.decodePlatformsMap(profile.platformsJson)
            val newPlatforms = currentPlatforms?.toMutableMap() ?: mutableMapOf()
            val removed = newPlatforms.remove(platformName)
            if (removed == null) {
                Log.d(TAG, "removePlatform: $platformName not in profile, skip")
                return@withContext
            }
            val updated = profile.copy(
                platformsJson = ContactMapper.encodePlatformsMap(newPlatforms),
                updateTime = System.currentTimeMillis()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            pushProfile(name = null, profile = buildProfileDto(updated))
        }
    }

    /**
     * [Phase 3] 直推 `PUT /api/user/profile`（仅传非空字段，服务端只更新传入字段）。
     *
     * 失败仅记日志，不阻塞本地保存；服务端权威，下次 sync 兜底。
     */
    private suspend fun pushProfile(name: String?, profile: ProfileDto) {
        try {
            serverApi.patchProfile(name = name, profile = profile)
            Log.d(TAG, "pushProfile OK: name=${name != null} platforms=${profile.contactMap.size}")
        } catch (e: Exception) {
            // [修复防御]: 直推失败不吞根因 —— 记日志 + 保留本地态,下次编辑/sync 补推。
            Log.w(TAG, "pushProfile: PUT /api/user/profile 失败(本地已保存)", e)
        }
    }

    /**
     * 由本地 `UserProfileCacheEntity` 构建服务端 `ProfileDto`：
     * `avatarPath → avatarURL`、`bio → description`、`platformsJson → contactMap`（value 非空条目）。
     *
     * [Phase 2] v8 全量映射：sex / country / region / birthday / backgroundURL / extra 不再静默丢失。
     */
    private fun buildProfileDto(profile: UserProfileCacheEntity): ProfileDto {
        val map = ContactMapper.decodePlatformsMap(profile.platformsJson)
            ?.mapNotNull { (k, v) -> v.value?.takeIf { it.isNotBlank() }?.let { k to it } }
            ?.toMap()
            ?: emptyMap()
        val extraObj = profile.extra?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { com.google.gson.JsonParser.parseString(raw).asJsonObject }
                .onFailure { Log.w(TAG, "buildProfileDto: extra JSON 解析失败,丢弃", it) }
                .getOrNull()
        }
        return ProfileDto(
            sex = profile.sex,
            avatarURL = profile.avatarPath,
            backgroundURL = profile.backgroundURL,
            description = profile.bio,
            country = profile.country,
            region = profile.region,
            birthday = profile.birthday,
            contactMap = map,
            extra = extraObj,
        )
    }

    private companion object {
        const val TAG = "UserProfileRepository"
    }
}
