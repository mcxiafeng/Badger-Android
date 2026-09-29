package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class Platform(
    @PrimaryKey val name: String,
    val displayName : String,
    val icon: String
)
