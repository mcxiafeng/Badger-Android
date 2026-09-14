package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
