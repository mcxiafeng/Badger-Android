package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(primaryKeys = ["userUuid", "personUuid"])
data class UserPersonRef(
    val userUuid: Uuid,
    val personUuid: Uuid
)
