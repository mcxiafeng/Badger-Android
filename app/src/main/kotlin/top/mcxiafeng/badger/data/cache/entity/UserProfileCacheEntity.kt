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
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q2 拍板
 */
@Entity(tableName = "user_profile_cache")
data class UserProfileCacheEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String = "",
    val avatarPath: String? = null,
    val bio: String? = null,
    val platformsJson: String = "{}",
    val defaultPlatform: String? = null,
    val updateTime: Long,
    val serverVersion: Long = 0L,
)
