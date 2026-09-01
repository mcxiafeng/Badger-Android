package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 联系人缓存表（映射自 v5 老 `contacts` 表）。
 *
 * V2 新增列：
 * - `serverId`：服务端 ID（V2 用 String 兼容服务端协议升级，V1 是 Long）
 * - `serverVersion`：服务端版本号（G1 乐观锁必需）
 * - `lastSyncedAt`：最后一次与服务端同步的时间戳
 * - `isLocalOnly`：true 表示未与服务端同步，P11 阶段启动后主动 sync；create-on-push 失败时，`serverId`
 *   临时保存本次 POST 使用的 client UUID，作为持久化幂等键
 * - `isDeleted`：软删除标志（V2 §3.2）
 * - `platformsJson`：把老 `platforms: Map<String, PlatformEntry>` 折叠为 JSON 字符串
 *   （老字段业务层完全不用，仅 schema 声明，迁移时一律写 `{}`）
 *
 * 老 `id` 仍是本地主键（自增），与 V1 保持兼容，避免破坏任何 by-id 引用。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 / §3.4
 */
@Entity(
    tableName = "contacts_cache",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isLocalOnly"]),
        Index(value = ["serverId"], unique = true),
    ]
)
data class ContactCacheEntity(
    @PrimaryKey val id: Long,
    /**
     * 服务端 Person uuid；当 `isLocalOnly=true` 且联系人来自一次失败的 create-on-push 时，
     * 本列暂存首次 POST 使用的 client UUID。重试成功后替换为服务端返回的 uuid。
     */
    val serverId: String? = null,
    val name: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val note: String? = null,
    val bio: String? = null,
    val pinyinInitial: String = "",
    /**
     * 社交平台折叠 JSON：`Map<String, PlatformEntry>` 的 Gson 序列化（V1 D1 决策保留本形状）。
     * 键 = platformKey（qq/wechat/...），值含 value / jumpLink / displayName 等展示字段。
     * 同步落盘时由 `profile.contactMap` 本地推导写入；本地纯新增行在直推成功后同样回填。
     */
    val platformsJson: String = "{}",
    val createTime: Long,
    val updateTime: Long,
    val lastSyncedAt: Long = 0L,
    val isLocalOnly: Boolean = true,
    val isDeleted: Boolean = false,
    /**
     * [Phase 2] v9 新增：服务端 `PersonDto.self` 持久化。
     * true = 当前用户的身份档案（禁删），null = 未知（旧数据升级后）。
     * UI 层可在离线时据此禁用删除按钮，避免误删 selfPerson 触发服务端 400。
     */
    val self: Boolean? = null,
)
