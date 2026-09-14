package top.mcxiafeng.badger.data.model

import androidx.compose.runtime.Immutable
import top.mcxiafeng.badger.data.cache.entity.CardCollectionCacheEntity

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
