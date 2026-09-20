package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(primaryKeys = ["personUuid", "tagsUuid"])
data class PersonTagsRef(
    val personUuid: Uuid,
    val tagsUuid: Uuid
)
