package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

@Entity
@Serializable
data class User(
    @PrimaryKey val uuid: Uuid,
    val profileUuid: Uuid,
    val name: String,
    val displayName: String,
    val token: String,
    val avatar: String,
    val email: String,
    val isAdmin: Boolean,
    val userSettings: JsonObject,
    val syncVersion: Long,
    val lastLogin: Long,
    val createTime: Long
)