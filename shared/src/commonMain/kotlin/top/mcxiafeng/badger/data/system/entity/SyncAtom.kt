package top.mcxiafeng.badger.data.system.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonObject
import top.mcxiafeng.badger.sync.EntityKind
import top.mcxiafeng.badger.sync.SyncType
import kotlin.uuid.Uuid

@Entity
data class SyncAtom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userUuid: Uuid? = null,
    val entityKind: EntityKind,
    val syncType: SyncType,
    val data: JsonObject,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
