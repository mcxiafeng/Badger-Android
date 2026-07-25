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
    indices = [Index(value = ["name"], unique = true)]
)
data class TagCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF1976D2L,
    val pinyinInitial: String = "",
    val source: String = "manual",
    val showDot: Boolean = true,
    val createTime: Long,
    val serverVersion: Long = 0L,
    val isLocalOnly: Boolean = true,
)
