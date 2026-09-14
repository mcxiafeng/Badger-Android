package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_collections_cache")
data class CardCollectionCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    val serverId: String? = null,
    val name: String,
    val description: String? = null,
    val backgroundImagePath: String? = null,
    val dominantColor: Long? = null,
    val coverAvatarUrl: String? = null,
    
    val personMembers: String = "[]",
    val createTime: Long,
    val isLocalOnly: Boolean = true,
)
