package top.mcxiafeng.badger.data.repository

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.PlatformEntry
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.queue.OperationTypes
import top.mcxiafeng.badger.data.queue.PendingUploadDao
import top.mcxiafeng.badger.data.queue.PendingUploadEntity
import top.mcxiafeng.badger.sync.DeviceIdProvider
import top.mcxiafeng.badger.sync.PendingUploadScheduler
import java.util.UUID

/**
 * [§14.2] Hilt `@Inject constructor` → Koin `singleOf(::UserProfileRepositoryImpl) { bind<UserProfileRepository>() }`。
 *
 * [V2-P12] 写操作(updatePlatformField / removePlatform / updateAvatarPath / saveUserProfile)接入
 * PendingUpload 队列(opType = USER_PROFILE_UPSERT)。UI 写本地 cache → enqueue op → kick Worker,
 * Worker 消费时调 `serverApi.patchMe()` → 服务端 ack 后清 op。
 *
 * 写前重读 + diff 防抖:[saveUserProfile] 与 [updateAvatarPath] / [updatePlatformField] / [removePlatform]
 * 都会查 DB 最新版比对,只在确实变化时入队 — 防止某些写两次(ProfileDetailPage 内部回调顺序
 * 不确定,可能 name / bio / platformsJson 各发一次),UI 视觉不会感知到 enqueue 次数,
 * 但 PendingUpload 表会塞满重复 op,Worker 反复打服务端。
 */
class UserProfileRepositoryImpl(
    private val userProfileCacheDao: UserProfileCacheDao,
    // [V2-P12] 接 PendingUpload 队列,与 ContactRepository(P5) 风格一致
    private val pendingDao: PendingUploadDao,
    private val pendingUploadScheduler: PendingUploadScheduler,
    private val deviceIdProvider: DeviceIdProvider,
) : UserProfileRepository {

    private val userProfileMutex = Mutex()

    override fun getUserProfile(): Flow<UserProfileCacheEntity?> = userProfileCacheDao.getProfile()

    override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = withContext(Dispatchers.IO) {
        userProfileCacheDao.getProfileOnce()
    }

    /**
     * 全量 save(ProfileDetailPage 的"保存"按钮直接调)。
     *
     * diff 后仅入队一条 op(PENDING),不会因为 name / bio / platformsJson 各字段都改而塞 3 条。
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
            if (dirty) {
                enqueueProfileUpsert(
                    displayName = profile.name,
                    bio = profile.bio,
                    avatarUrl = profile.avatarPath,
                    platformsJson = profile.platformsJson,
                )
            } else {
                Log.d(TAG, "saveUserProfile: 无变化,跳过 enqueue")
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
                Log.d(TAG, "updateAvatarPath: no change (both ${SafeLogOrEmpty(avatarPath)}), skip")
                return@withContext
            }
            // [修复防御]: 即使是"清空头像"也要走队列入队(opType=USER_PROFILE_UPSERT,avatar_url=null),
            // 否则远端永远显示老头像。
            userProfileCacheDao.saveProfile(profile.copy(avatarPath = avatarPath, updateTime = System.currentTimeMillis()))
            userProfileCacheDao.bumpProfile()
            enqueueProfileUpsert(
                displayName = null,
                bio = null,
                avatarUrl = avatarPath,
                platformsJson = null,
            )
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
            val updatedPlatformsJson = ContactMapper.encodePlatformsMap(newPlatforms)
            val updated = profile.copy(
                platformsJson = updatedPlatformsJson,
                updateTime = System.currentTimeMillis()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            enqueueProfileUpsert(
                displayName = null,
                bio = null,
                avatarUrl = null,
                platformsJson = updatedPlatformsJson,
            )
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
            val updatedPlatformsJson = ContactMapper.encodePlatformsMap(newPlatforms)
            val updated = profile.copy(
                platformsJson = updatedPlatformsJson,
                updateTime = System.currentTimeMillis()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            enqueueProfileUpsert(
                displayName = null,
                bio = null,
                avatarUrl = null,
                platformsJson = updatedPlatformsJson,
            )
        }
    }

    /**
     * [V2-P12] 集中 enqueue USER_PROFILE_UPSERT op。
     *
     * payloadJson 字段对齐 `serverApi.patchMe`:`display_name` / `bio` / `avatar_url` / `platforms_json`。
     * contactId 占位 -1L(PendingUploadEntity.contactId NOT NULL,profile 域没 contact 概念)。
     */
    private suspend fun enqueueProfileUpsert(
        displayName: String?,
        bio: String?,
        avatarUrl: String?,
        platformsJson: String?,
    ) {
        val opId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            displayName?.let { addProperty("display_name", it) }
            bio?.let { addProperty("bio", it) }
            avatarUrl?.let { addProperty("avatar_url", it) }
            platformsJson?.let { addProperty("platforms_json", it) }
        }
        pendingDao.enqueue(
            PendingUploadEntity(
                opId = opId,
                contactId = -1L,
                opType = OperationTypes.USER_PROFILE_UPSERT,
                resourceVersion = 0L,
                payloadJson = payload.toString(),
                createdAt = now,
                status = "PENDING",
                deviceId = deviceIdProvider.deviceId(),
            )
        )
        pendingUploadScheduler.kick()
        Log.d(TAG, "enqueueProfileUpsert: opId=${opId.take(8)} display=${displayName != null} bio=${bio != null} avatar=${avatarUrl != null} platforms=${platformsJson != null}")
    }

    private fun SafeLogOrEmpty(s: String?): String =
        if (s.isNullOrBlank()) "<empty>" else "<len=${s.length}>"

    private companion object {
        const val TAG = "UserProfileRepository"
    }
}