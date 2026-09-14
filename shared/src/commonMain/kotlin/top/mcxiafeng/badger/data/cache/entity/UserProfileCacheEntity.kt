package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile_cache")
data class UserProfileCacheEntity(
    @PrimaryKey val id: Long = 1L,
    val name: String = "",
    val avatarPath: String? = null,
    val bio: String? = null,
    

    val platformsJson: String = "{}",
    val defaultPlatform: String? = null,
    val updateTime: Long,
    
    val sex: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val backgroundURL: String? = null,
    
    val extra: String? = null,
)
