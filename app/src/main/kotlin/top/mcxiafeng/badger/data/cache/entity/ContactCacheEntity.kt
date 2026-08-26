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
 * - `isLocalOnly`：true 表示未与服务端同步，P11 阶段启动后主动 sync
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
        Index(value = ["serverId"]),
    ]
)
data class ContactCacheEntity(
    @PrimaryKey val id: Long,
    /**
     * [Phase 3] 服务端 Person uuid（新 Java `/api` 契约）。旧 Go 契约的 serverId 语义
     * 复用本列：同步后的行这里存服务端 uuid，本地纯新增行（isLocalOnly=true）为 null。
     */
    val serverId: String? = null,
    val name: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val note: String? = null,
    val bio: String? = null,
    val pinyinInitial: String = "",
    /**
     * [Phase 3] 服务端 `profile` 对象完整 JSON 文本（camelCase 字段，含 contactMap/extra）。
     * 同步落盘时写入；本地纯新增行在直推成功后同样回填。
     */
    val platformsJson: String = "{}",
    val createTime: Long,
    val updateTime: Long,
    val lastSyncedAt: Long = 0L,
    val isLocalOnly: Boolean = true,
    val isDeleted: Boolean = false,
)
