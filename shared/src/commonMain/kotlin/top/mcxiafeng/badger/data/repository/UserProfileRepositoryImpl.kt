package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.data.model.PlatformEntry
import top.mcxiafeng.badger.data.cache.dao.UserProfileCacheDao
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.ContactMapper.toPlatformsJson
import top.mcxiafeng.badger.network.BadgerJson
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.ProfileDto
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.UserProfileResponse
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.shared.util.nowMs

class UserProfileRepositoryImpl(
    private val userProfileCacheDao: UserProfileCacheDao,
    private val serverApi: ServerApi,
) : UserProfileRepository {

    private val userProfileMutex = Mutex()

    override fun getUserProfile(): Flow<UserProfileCacheEntity?> = userProfileCacheDao.getProfile()

    override suspend fun getUserProfileOnce(): UserProfileCacheEntity? = withContext(BadgerDispatchers.io) {
        userProfileCacheDao.getProfileOnce()
    }

    

    override suspend fun saveUserProfile(profile: UserProfileCacheEntity): Unit = userProfileMutex.withLock {
        withContext(BadgerDispatchers.io) {
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
                BadgerLog.d(TAG, "saveUserProfile: 无变化,跳过推送")
            }
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
        withContext(BadgerDispatchers.io) {
            val profile = userProfileCacheDao.getProfileOnce()
                ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs())
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
                updateTime = nowMs()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            pushProfile(name = null, profile = buildProfileDto(updated))
        }
    }

    override suspend fun removePlatform(platformName: String) = userProfileMutex.withLock {
        withContext(BadgerDispatchers.io) {
            val profile = userProfileCacheDao.getProfileOnce() ?: return@withContext
            val currentPlatforms = ContactMapper.decodePlatformsMap(profile.platformsJson)
            val newPlatforms = currentPlatforms?.toMutableMap() ?: mutableMapOf()
            val removed = newPlatforms.remove(platformName)
            if (removed == null) {
                BadgerLog.d(TAG, "removePlatform: $platformName not in profile, skip")
                return@withContext
            }
            val updated = profile.copy(
                platformsJson = ContactMapper.encodePlatformsMap(newPlatforms),
                updateTime = nowMs()
            )
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            pushProfile(name = null, profile = buildProfileDto(updated))
        }
    }

    override suspend fun editUserProfile(
        transform: (UserProfileCacheEntity) -> UserProfileCacheEntity,
    ): UserProfileCacheEntity = userProfileMutex.withLock {
        withContext(BadgerDispatchers.io) {
            val existing = userProfileCacheDao.getProfileOnce()
            val updated = transform(existing ?: UserProfileCacheEntity(name = "用户", updateTime = nowMs()))
            if (existing != null && existing == updated) {
                BadgerLog.d(TAG, "editUserProfile: 无变化,跳过落库")
                return@withContext updated
            }
            userProfileCacheDao.saveProfile(updated.copy(updateTime = nowMs()))
            userProfileCacheDao.bumpProfile()
            BadgerLog.d(TAG, "editUserProfile: name=${updated.name} platforms=${updated.platformsJson?.length ?: 0}字符")
            
            
            pushProfile(name = updated.name, profile = buildProfileDto(updated))
            updated
        }
    }

    

    private suspend fun pushProfile(name: String?, profile: ProfileDto) {
        try {
            serverApi.patchProfile(name = name, profile = profile)
            BadgerLog.d(TAG, "pushProfile OK: name=${name != null} platforms=${profile.contactMap.size}")
        } catch (e: Exception) {
            
            BadgerLog.w(TAG, "pushProfile: PUT /api/user/profile 失败(本地已保存)", e)
        }
    }

    

    override suspend fun applyRemoteProfile(resp: UserProfileResponse): Unit = userProfileMutex.withLock {
        withContext(BadgerDispatchers.io) {
            rememberSelfPersonId(resp.selfPersonId)
            val existing = userProfileCacheDao.getProfileOnce()
            val serverPlatforms = resp.profile
                ?.let { ContactMapper.decodePlatformsMap(it.toPlatformsJson()) }
                ?: emptyMap()
            val localPlatforms = ContactMapper.decodePlatformsMap(existing?.platformsJson) ?: emptyMap()
            
            
            
            val unionPlatforms = serverPlatforms.toMutableMap().apply {
                localPlatforms.forEach { (key, entry) ->
                    if (!containsKey(key) && (!entry.value.isNullOrBlank() || entry.jumpLink.isNotBlank())) put(key, entry)
                }
            }
            val base = existing ?: UserProfileCacheEntity(name = "", updateTime = nowMs())
            val merged = base.copy(
                name = resp.displayName ?: resp.name ?: base.name,
                bio = resp.profile?.description ?: base.bio,
                avatarPath = resp.profile?.avatarURL ?: base.avatarPath,
                sex = resp.profile?.sex ?: base.sex,
                country = resp.profile?.country ?: base.country,
                region = resp.profile?.region ?: base.region,
                birthday = resp.profile?.birthday ?: base.birthday,
                backgroundURL = resp.profile?.backgroundURL ?: base.backgroundURL,
                extra = resp.profile?.extra?.toString()?.takeIf { it.isNotBlank() } ?: base.extra,
                platformsJson = ContactMapper.encodePlatformsMap(unionPlatforms).takeIf { unionPlatforms.isNotEmpty() }
                    ?: base.platformsJson,
                
                updateTime = base.updateTime,
            )
            if (existing != null && existing == merged) {
                BadgerLog.d(TAG, "applyRemoteProfile: 无变化,跳过落库")
                return@withContext
            }
            userProfileCacheDao.saveProfile(merged.copy(updateTime = nowMs()))
            userProfileCacheDao.bumpProfile()
            BadgerLog.d(TAG, "applyRemoteProfile: merged name=${merged.name} platforms=${unionPlatforms.size}")
        }
    }

    

    override suspend fun applySyncedSelfPerson(person: PersonDto): Unit = userProfileMutex.withLock {
        withContext(BadgerDispatchers.io) {
            if (person.uuid.isBlank()) {
                BadgerLog.w(TAG, "applySyncedSelfPerson: uuid 缺失,忽略该事件")
                return@withContext
            }
            AuthPrefs.writeSelfPersonId(person.uuid)
            val base = userProfileCacheDao.getProfileOnce()
                ?: UserProfileCacheEntity(name = "", updateTime = nowMs())
            val profile = person.profile
            val updated = if (profile == null) {
                BadgerLog.w(TAG, "applySyncedSelfPerson: ${person.uuid.take(8)} 事件无 profile,仅刷 name")
                base.copy(name = person.name.ifBlank { base.name }, updateTime = nowMs())
            } else {
                
                
                val serverPlatforms = ContactMapper.decodePlatformsMap(profile.toPlatformsJson()) ?: emptyMap()
                val mergedPlatforms = serverPlatforms.toMutableMap().apply {
                    ContactMapper.decodePlatformsMap(base.platformsJson)?.forEach { (key, entry) ->
                        if (!containsKey(key) && entry.value.isNullOrBlank() && entry.jumpLink.isNotBlank()) {
                            put(key, entry)
                        }
                    }
                }
                base.copy(
                    name = person.name.ifBlank { base.name },
                    bio = profile.description,
                    avatarPath = profile.avatarURL,
                    sex = profile.sex,
                    country = profile.country,
                    region = profile.region,
                    birthday = profile.birthday,
                    backgroundURL = profile.backgroundURL,
                    extra = profile.extra?.toString()?.takeIf { it.isNotBlank() },
                    platformsJson = ContactMapper.encodePlatformsMap(mergedPlatforms),
                    updateTime = nowMs(),
                )
            }
            userProfileCacheDao.saveProfile(updated)
            userProfileCacheDao.bumpProfile()
            BadgerLog.d(
                TAG,
                "applySyncedSelfPerson: uuid=${person.uuid.take(8)} name=${updated.name} platforms=${profile?.contactMap?.size ?: 0}",
            )
        }
    }

    
    private fun rememberSelfPersonId(uuid: String?) {
        val id = uuid?.takeIf { it.isNotBlank() } ?: return
        if (AuthPrefs.readSelfPersonId() == id) return
        AuthPrefs.writeSelfPersonId(id)
        BadgerLog.d(TAG, "rememberSelfPersonId: ${id.take(8)}... 已持久化")
    }

    override suspend fun refreshFromServer(): Boolean = withContext(BadgerDispatchers.io) {
        try {
            val resp = serverApi.getProfile()
            applyRemoteProfile(resp)
            BadgerLog.d(TAG, "refreshFromServer: self 档案已刷新 selfPersonId=${resp.selfPersonId?.take(8)}")
            true
        } catch (e: Exception) {
            BadgerLog.w(TAG, "refreshFromServer: 拉取 self 档案失败", e)
            false
        }
    }

    

    private suspend fun buildProfileDto(profile: UserProfileCacheEntity): ProfileDto {
        val platformsMap = ContactMapper.decodePlatformsMap(profile.platformsJson)
        val map = platformsMap
            ?.mapNotNull { (k, v) -> v.value?.takeIf { it.isNotBlank() }?.let { k to it } }
            ?.toMap()
            ?: emptyMap()
        val extraObj = profile.extra?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { BadgerJson.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject }
                .onFailure { BadgerLog.w(TAG, "buildProfileDto: extra JSON 解析失败,丢弃", it) }
                .getOrNull()
        }
        return ProfileDto(
            sex = profile.sex,
            avatarURL = resolveAvatarUrl(profile.avatarPath),
            backgroundURL = profile.backgroundURL,
            description = profile.bio,
            country = profile.country,
            region = profile.region,
            birthday = profile.birthday,
            contactMap = map,
            
            
            extra = ContactMapper.mergePlatformMetaIntoExtra(extraObj, platformsMap),
        )
    }

    

    private var lastUploadedAvatar: Pair<String, String>? = null

    

    private suspend fun resolveAvatarUrl(avatarPath: String?): String? {
        if (avatarPath.isNullOrBlank()) return null
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) return avatarPath
        val bytes = withContext(BadgerDispatchers.io) { ImageFiles.loadImageBytes(avatarPath) }
            ?: run {
                BadgerLog.w(TAG, "resolveAvatarUrl: 本地头像文件读取失败 path=…${avatarPath.takeLast(12)}, avatarURL 置空")
                return null
            }
        val cacheKey = "${avatarPath}:${bytes.size}:${bytes.contentHashCode()}"
        lastUploadedAvatar?.takeIf { it.first == cacheKey }?.let {
            BadgerLog.d(TAG, "resolveAvatarUrl: 命中会话缓存 url=${it.second}")
            return it.second
        }
        val ext = avatarPath.substringAfterLast('.', "webp").lowercase()
        val url = try {
            serverApi.uploadImage(bytes, "avatar.$ext")
        } catch (e: Exception) {
            BadgerLog.w(TAG, "resolveAvatarUrl: 头像上传失败,中止本次资料推送(本地已保存)", e)
            throw IllegalStateException("头像上传失败,已中止推送以保护服务端头像", e)
        }
        lastUploadedAvatar = cacheKey to url
        BadgerLog.d(TAG, "resolveAvatarUrl: 上传成功 url=$url")
        return url
    }

    private companion object {
        const val TAG = "UserProfileRepository"
    }
}
