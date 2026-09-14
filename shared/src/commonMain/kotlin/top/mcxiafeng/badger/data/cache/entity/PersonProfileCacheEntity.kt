package top.mcxiafeng.badger.data.cache.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "person_profile_cache",
    foreignKeys = [
        ForeignKey(
            entity = ContactCacheEntity::class,
            parentColumns = ["serverId"],
            childColumns = ["contactServerId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class PersonProfileCacheEntity(
    
    @PrimaryKey val contactServerId: String,
    val sex: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val backgroundURL: String? = null,
    
    val extra: String? = null,
)
