package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 字段对齐服务端 GET /api/user/collections 响应（fastjson2 省略 null 字段）：
 * backgroundURL/personMembers 可缺省，ownerUuid 服务端会回填但保留可空兜底旧版 jar。
 */
@Entity
@Serializable
data class Collection(
    @PrimaryKey val uuid: Uuid,
    val name: String,
    val description: String? = null,
    val ownerUuid: Uuid? = null,
    val backgroundURL: String? = null,
    val personMembers: List<Uuid> = emptyList(),
    val createTime: Long = 0L,
)
