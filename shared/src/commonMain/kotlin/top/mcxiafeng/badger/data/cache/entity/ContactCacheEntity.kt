package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts_cache",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isLocalOnly"]),
        Index(value = ["serverId"], unique = true),
    ]
)
data class ContactCacheEntity(
    
    
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    

    val serverId: String? = null,
    val name: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val note: String? = null,
    val bio: String? = null,
    val pinyinInitial: String = "",
    

    val platformsJson: String = "{}",
    val createTime: Long,
    val updateTime: Long,
    val lastSyncedAt: Long = 0L,
    val isLocalOnly: Boolean = true,
    val isDeleted: Boolean = false,
    

    val self: Boolean? = null,
)
