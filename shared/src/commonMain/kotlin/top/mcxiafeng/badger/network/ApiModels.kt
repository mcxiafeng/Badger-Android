package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [KMP K08-B] ServerApi 的 DTO（从 app network 包迁入 commonMain）。
 * 手写 from(JsonObject) 防御语义逐行平移（K04 约定勿改行为）。
 */

/** 服务端 Person 行。[self]=true 表示当前用户身份档案。 */
@Serializable
data class PersonDto(
    val uuid: String = "",
    val name: String = "",
    val profile: ProfileDto? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val self: Boolean = false,
) {
    /** 服务端 createTime → epoch millis；解析失败回退 0（不炸调用方）。 */
    fun createTimeMillis(): Long = parseServerDateMillis(createTime)

    /** 服务端 updateTime → epoch millis；解析失败回退 0。 */
    fun updateTimeMillis(): Long = parseServerDateMillis(updateTime)

    companion object {
        fun from(o: JsonObject): PersonDto = PersonDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            profile = jsonObjectOrNull(o, "profile")?.let { ProfileDto.from(it) },
            createTime = stringOrNull(o, "createTime"),
            updateTime = stringOrNull(o, "updateTime"),
            self = boolOr(o["self"], false),
        )
    }
}

/** 服务端 Person 的嵌套 Profile。 */
@Serializable
data class ProfileDto(
    val sex: String? = null,
    val avatarURL: String? = null,
    val backgroundURL: String? = null,
    val description: String? = null,
    val country: String? = null,
    val region: String? = null,
    val birthday: String? = null,
    val contactMap: Map<String, String> = emptyMap(),
    val extra: JsonObject? = null,
) {
    /** 序列化回服务端 `profile` 载荷（无值字段省略，服务端只更新传入字段）。 */
    fun toJsonObject(): JsonObject = buildJsonObject {
        sex?.let { put("sex", it) }
        avatarURL?.let { put("avatarURL", it) }
        backgroundURL?.let { put("backgroundURL", it) }
        description?.let { put("description", it) }
        country?.let { put("country", it) }
        region?.let { put("region", it) }
        birthday?.let { put("birthday", it) }
        if (contactMap.isNotEmpty()) {
            put("contactMap", JsonObject(contactMap.mapValues { JsonPrimitive(it.value) }))
        }
        extra?.let { put("extra", it) }
    }

    companion object {
        fun from(o: JsonObject): ProfileDto = ProfileDto(
            sex = stringOrNull(o, "sex"),
            avatarURL = stringOrNull(o, "avatarURL"),
            backgroundURL = stringOrNull(o, "backgroundURL"),
            description = stringOrNull(o, "description"),
            country = stringOrNull(o, "country"),
            region = stringOrNull(o, "region"),
            birthday = stringOrNull(o, "birthday"),
            contactMap = parseStringMap(jsonObjectOrNull(o, "contactMap")),
            extra = jsonObjectOrNull(o, "extra"),
        )
    }
}

/**
 * 服务端 owner 域变更日志快照：Tag 行 `{uuid, name, colorHash, personMembers, createTime}`，
 * Collection 行 `{uuid, name, description, backgroundURL, personMembers, createTime}`。
 */
@Serializable
data class TagDto(
    val uuid: String = "",
    val name: String = "",
    val colorHash: String? = null,
    val personMembers: List<String> = emptyList(),
    val createTime: String? = null,
) {
    companion object {
        fun from(o: JsonObject): TagDto = TagDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            colorHash = stringOrNull(o, "colorHash"),
            personMembers = parseStringArray(jsonArrayOrNull(o, "personMembers")),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

@Serializable
data class CollectionDto(
    val uuid: String = "",
    val name: String = "",
    val description: String? = null,
    val backgroundURL: String? = null,
    val personMembers: List<String> = emptyList(),
    val createTime: String? = null,
) {
    companion object {
        fun from(o: JsonObject): CollectionDto = CollectionDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            description = stringOrNull(o, "description"),
            backgroundURL = stringOrNull(o, "backgroundURL"),
            personMembers = parseStringArray(jsonArrayOrNull(o, "personMembers")),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

/** 服务端 UserHistory 变更行 → 增量重放单元。 */
@Serializable
data class SyncChange(
    val version: Long = 0L,
    val type: String = "",
    val objectName: String = "",
    val objectId: String? = null,
    val fieldName: String? = null,
    val value: JsonElement? = null,
) {
    companion object {
        fun from(o: JsonObject): SyncChange = SyncChange(
            version = longOr(o["version"], 0L),
            type = stringOrNull(o, "type").orEmpty(),
            objectName = stringOrNull(o, "objectName").orEmpty(),
            objectId = stringOrNull(o, "objectId"),
            fieldName = stringOrNull(o, "fieldName"),
            value = o["value"],
        )
    }
}

/** `GET /api/user/sync?since=` 增量拉取结果：[version] 为下一轮 since，[hasMore] 提示续拉。 */
@Serializable
data class SyncPage(
    val version: Long = 0L,
    val changes: List<SyncChange> = emptyList(),
    val hasMore: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): SyncPage = SyncPage(
            version = longOr(o["version"], 0L),
            changes = jsonArrayOrNull(o, "changes")?.mapNotNull { el ->
                runCatching { SyncChange.from(el as JsonObject) }.getOrNull()
            } ?: emptyList(),
            hasMore = boolOr(o["hasMore"], false),
        )
    }
}

/**
 * 服务端 Date 字符串或 epoch 数值解析为 epoch millis；失败回退 0。
 * [KMP K08-B] 手写解析替代 java.text.SimpleDateFormat（common 不可用）：
 * 优先 epoch 数值，否则解析 `yyyy[-MM[-dd[ HH:mm[:ss]]]]` 数字段。
 */
fun parseServerDateMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val s = raw.trim()
    s.toLongOrNull()?.let {
        // 秒级时间戳转毫秒，毫秒级原样返回
        return if (it in 1_000_000_000L..99_999_999_999L) it * 1000L else it
    }
    s.toDoubleOrNull()?.let { d ->
        return if (d in 1_000_000_000.0..99_999_999_999.0) (d * 1000.0).toLong() else d.toLong()
    }
    // fastjson2 默认格式: yyyy-MM-dd HH:mm:ss（支持 T 分隔与尾 Z；各字段手工解析）
    val cleaned = s.replace('T', ' ').trimEnd('Z')
    return try {
        val dateAndTime = cleaned.split(' ', limit = 2)
        val dateParts = dateAndTime[0].split('-')
        val year = dateParts[0].toInt()
        val month = dateParts.getOrElse(1) { "1" }.toInt()
        val day = dateParts.getOrElse(2) { "1" }.toInt()
        var hour = 0; var minute = 0; var second = 0
        if (dateAndTime.size > 1) {
            val timeParts = dateAndTime[1].split(':')
            hour = timeParts.getOrElse(0) { "0" }.trim().toIntOrNull() ?: 0
            minute = timeParts.getOrElse(1) { "0" }.trim().toIntOrNull() ?: 0
            second = timeParts.getOrElse(2) { "0" }.trim().toIntOrNull() ?: 0
        }
        daysFromEpoch(year, month, day) * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L
    } catch (_: Exception) {
        0L
    }
}

/** 公历 y/m/d → 自 1970-01-01 起的天数（proleptic Gregorian；仅用于日期差换算）。 */
private fun daysFromEpoch(year: Int, month: Int, day: Int): Long {
    var y = year.toLong()
    val m = month.toLong()
    y -= if (m <= 2) 1 else 0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}
