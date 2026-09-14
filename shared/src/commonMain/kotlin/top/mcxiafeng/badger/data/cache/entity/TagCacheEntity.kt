package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags_cache",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["serverId"]),
    ]
)
data class TagCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    

    val serverId: String? = null,
    val name: String,
    val color: Long = 0xFF1976D2L,
    
    val colorHash: String? = null,
    

    val personMembers: String = "[]",
    val pinyinInitial: String = "",
    val source: String = "manual",
    val showDot: Boolean = true,
    val createTime: Long,
    val isLocalOnly: Boolean = true,
)
