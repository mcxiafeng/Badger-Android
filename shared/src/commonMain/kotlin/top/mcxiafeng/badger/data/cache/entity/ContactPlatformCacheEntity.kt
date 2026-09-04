package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 联系人平台条目表（映射自 v5 老 `contact_platforms` 表）。
 *
 * 字段一一对应 + V2 列 `serverVersion` / `isLocalOnly`。
 * 保留 `(contactId, platformKey)` 唯一索引，确保同一联系人在同一平台不重复。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2
 */
@Entity(
    tableName = "contact_platforms_cache",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["platformKey"]),
        Index(value = ["contactId", "platformKey"], unique = true),
    ]
)
data class ContactPlatformCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val platformKey: String,
    val value: String? = null,
    val displayName: String? = null,
    val jumpLink: String = "",
    val originalLink: String? = null,
    val avatarUrl: String? = null,
    val isLocalOnly: Boolean = true,
)
