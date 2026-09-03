package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity

/**
 * 名片夹及联系人数量。
 *
 * [A3] 用扁平投影（避免 @Embedded 与 cache JOIN 冲突）。
 * KSP 必须在 @Database 同 module 看到该类，所以它是 data/model 顶层定义。
 */
@Immutable
data class CardCollectionWithCount(
    @androidx.room.ColumnInfo(name = "id") val id: Long,
    @androidx.room.ColumnInfo(name = "name") val name: String,
    @androidx.room.ColumnInfo(name = "description") val description: String?,
    @androidx.room.ColumnInfo(name = "backgroundImagePath") val backgroundImagePath: String?,
    @androidx.room.ColumnInfo(name = "dominantColor") val dominantColor: Long?,
    @androidx.room.ColumnInfo(name = "coverAvatarUrl") val coverAvatarUrl: String?,
    @androidx.room.ColumnInfo(name = "createTime") val createTime: Long,
    @androidx.room.ColumnInfo(name = "isLocalOnly") val isLocalOnly: Boolean,
    @androidx.room.ColumnInfo(name = "contactCount") val contactCount: Int,
) {
    /**
     * UI 投影 → 实体的便捷转换。
     *
     * [T08 警告] 本转换**不携带 identity 字段**（serverId / personMembers 丢失），仅限 UI 展示
     * 与只读场景。**禁止把返回值直接用于写路径**（@Update 全行覆盖会把已同步名片夹孤立出同步
     * 体系，F3）。写路径必须经 `sync/Identity.kt` 的 `rebaseCollection` 用 DB existing 行 rebase。
     */
    fun toCacheEntity(): CardCollectionCacheEntity = CardCollectionCacheEntity(
        id = id,
        name = name,
        description = description,
        backgroundImagePath = backgroundImagePath,
        dominantColor = dominantColor,
        coverAvatarUrl = coverAvatarUrl,
        createTime = createTime,
        isLocalOnly = isLocalOnly,
    )
}
