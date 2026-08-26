package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * V2 标签表（映射自 v5 老 `tags` 表）。
 *
 * Q2 决策：**保留** `showDot` / `source` UI 字段（v5 schema 新增项）。
 * - `showDot`：列表项右侧色点开关
 * - `source`：manual / ai / legacy / import，便于"清空 AI 标签"等批量操作
 *
 * V2 新增列：`serverVersion` / `isLocalOnly`。
 * `name` 唯一索引保留，确保"按标签名去重"语义稳定。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q2 拍板
 */
@Entity(
    tableName = "tags_cache",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["serverId"]),
    ]
)
data class TagCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * [Phase 3] 服务端 Tag uuid（新 Java `/api` 契约）。直推创建后服务端分配，回填本列。
     */
    val serverId: String? = null,
    val name: String,
    val color: Long = 0xFF1976D2L,
    /** [Phase 3] 服务端 colorHash（客户端统一用 `0xRRGGBBAA` hex 串）。 */
    val colorHash: String? = null,
    /**
     * [Phase 3] 服务端 Tag.personMembers `[personUuid]` 的 JSON 文本（与 contact_tag_cache
     * 多对多表互补：本列保证 sync 往返无损，contact_tag_cache 供本地 UI 查询）。
     */
    val personMembers: String = "[]",
    val pinyinInitial: String = "",
    val source: String = "manual",
    val showDot: Boolean = true,
    val createTime: Long,
    val isLocalOnly: Boolean = true,
)
