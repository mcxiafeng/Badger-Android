package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(primaryKeys = ["collectionUuid", "personUuid"])
data class CollectionPersonRef(
    val collectionUuid: Uuid,
    val personUuid: Uuid
)
