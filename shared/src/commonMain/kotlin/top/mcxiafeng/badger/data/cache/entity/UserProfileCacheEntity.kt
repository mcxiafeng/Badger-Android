package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * V2 用户资料表（映射自 v5 老 `user_profile` 表）。
 *
 * Q2 决策：
 * - **保留** `avatarPath`（本地头像文件路径，用户换手机前能继续渲染）
 * - **保留** `defaultPlatform`（UI 状态）
 * - **丢** `cardImagePath`（V2 改用服务端 `coverAvatarUrl`，本地不再需要缓存）
 * - `platforms`（空 Map）→ `platformsJson` JSON 字符串占位
 *
 * 单例记录（id = 1L），与 V1 一致。
 *
 * [Phase 2] v8 新增 ProfileDto 全字段：sex/country/region/birthday/backgroundURL/extra。
 * 原先 buildProfileDto 只映射 avatarPath→avatarURL、bio→description、platformsJson→contactMap，
 * 这 6 个字段静默丢失；加列后 buildProfileDto 可全量回推。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q2 拍板
 */
@Entity(tableName = "user_profile_cache")
data class UserProfileCacheEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String = "",
    val avatarPath: String? = null,
    val bio: String? = null,
    /**
     * 社交平台折叠 JSON：`Map<String, PlatformEntry>` 的 Gson 序列化（V1 D1 决策保留本形状）。
     * 推送时由本列反向推导 `ProfileDto.contactMap`（value 非空条目）。
     */
    val platformsJson: String = "{}",
    val defaultPlatform: String? = null,
    val updateTime: Long,
    // [Phase 2] v8 新增 ProfileDto 全字段（nullable，旧数据升级后为 null）
    val sex: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val backgroundURL: String? = null,
    /** ProfileDto.extra 原始 JSON（`Map<String,Map<String,Object>>`），客户端透传。 */
    val extra: String? = null,
)
