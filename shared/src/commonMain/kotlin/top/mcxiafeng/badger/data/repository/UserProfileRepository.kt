package top.mcxiafeng.badger.data.repository

import kotlinx.coroutines.flow.Flow
import top.mcxiafeng.badger.data.cache.entity.UserProfileCacheEntity
import top.mcxiafeng.badger.network.PersonDto
import top.mcxiafeng.badger.network.UserProfileResponse

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

    /**
     * 互斥锁内的读-改-写：读最新快照 → [transform] → 落库（无变化跳过写 + 不 bump）。
     *
     * UI 层禁止"getUserProfileOnce → copy → saveUserProfile"三段式——锁外快照与并发写者
     * （平台自动昵称回写、头像上传、sync selfPerson 路由）互踩会整段覆盖丢字段，
     * 所有局部编辑必须经此入口。
     */
    suspend fun editUserProfile(transform: (UserProfileCacheEntity) -> UserProfileCacheEntity): UserProfileCacheEntity

    /**
     * 合并服务端下行资料（登录引导 bootstrap 用）。互斥锁内读-改-写。
     *
     * 基础字段刷平（服务端值优先，null 不覆盖本地）；platformsJson 走 **union**——
     * 服务端条目优先，本地独有（value 非空）保留，避免"离线先建资料再登录"丢平台。
     * 同时把 [UserProfileResponse.selfPersonId] 持久化到 AuthPrefs。
     */
    suspend fun applyRemoteProfile(resp: UserProfileResponse)

    /**
     * 应用 sync 通道的 selfPerson 事件（ADD 快照 / profile UPDATE）。互斥锁内读-改-写。
     *
     * 服务器权威整段覆盖（含 platformsJson——多设备平台同步的唯一路径），
     * defaultPlatform 属设备本地偏好，保留不覆盖。同时把 selfPersonId 持久化到 AuthPrefs。
     */
    suspend fun applySyncedSelfPerson(person: PersonDto)
}
