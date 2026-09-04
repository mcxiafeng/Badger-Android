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
    /** [Phase 3] 服务端 Collection uuid（新 Java `/api` 契约）。直推创建后回填。 */
    val serverId: String? = null,
    val name: String,
    val description: String? = null,
    val backgroundImagePath: String? = null,
    val dominantColor: Long? = null,
    val coverAvatarUrl: String? = null,
    /** [Phase 3] 服务端 Collection.personMembers `[personUuid]` 的 JSON 文本。 */
    val personMembers: String = "[]",
    val createTime: Long,
    val isLocalOnly: Boolean = true,
)
