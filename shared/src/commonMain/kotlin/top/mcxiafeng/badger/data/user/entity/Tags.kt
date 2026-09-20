package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Entity
@Serializable
data class Tags(
    @PrimaryKey val uuid: Uuid,
    val name: String,
    val colorHash: String? = null,
    val personMembers: List<Uuid> = emptyList(),
    val createTime: Long = 0L
)
