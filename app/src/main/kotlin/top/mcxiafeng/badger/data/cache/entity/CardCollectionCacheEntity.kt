package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * V2 名片夹表（映射自 v5 老 `card_collections` 表）。
 *
 * Q3 决策：**保留** `backgroundImagePath` / `dominantColor` 老字段（CardComponents/CardDialogs
 * 仍依赖这些字段做背景渲染）。新增 `coverAvatarUrl` 占位（V2 协议上线后由服务端 sync 写入）。
 *
 * V2 新增列：`serverVersion` / `isLocalOnly`。
 *
 * 对应规约：[V2-P1] docs/BADGER_V2_CLIENT_PLAN.md §3.2 + Q3 拍板
 */
@Entity(tableName = "card_collections_cache")
data class CardCollectionCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val backgroundImagePath: String? = null,
    val dominantColor: Long? = null,
    val coverAvatarUrl: String? = null,
    val createTime: Long,
    val serverVersion: Long = 0L,
    val isLocalOnly: Boolean = true,
)
