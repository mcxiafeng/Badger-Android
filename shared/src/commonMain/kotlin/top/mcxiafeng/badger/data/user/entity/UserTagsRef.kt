package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(primaryKeys = ["userUuid", "tagsUuid"])
data class UserTagsRef(
    val userUuid: Uuid,
    val tagsUuid: Uuid
)
