package top.mcxiafeng.badger.data.user.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

/**
 * 字段对齐服务端 GET /api/user/profile 响应（fastjson2 省略 null 字段）：
 * 除 uuid 外全部可缺省，extra 为嵌套 JSON 对象原样承载。
 */
@Entity
@Serializable
data class Profile(
    @PrimaryKey val uuid: Uuid,
    val sex: String? = null,
    val backgroundURL: String? = null,
    val description: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val contactMap: Map<String, String> = emptyMap(),
    val extra: JsonObject = JsonObject(emptyMap()),
)
