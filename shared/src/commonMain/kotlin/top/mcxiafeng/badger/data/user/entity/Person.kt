package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Entity
@Serializable
data class Person(
    @PrimaryKey val uuid: Uuid,
    val ownerId: Uuid,
    val profileId: Uuid,
    val name: String? = null,
    val avatarURL: String? = null,
    val createTime: Long? = null,
    val updateTime: Long? = null
)
