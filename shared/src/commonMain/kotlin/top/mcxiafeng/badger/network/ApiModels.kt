package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

@Serializable
data class PersonDto(
    val uuid: String = "",
    val name: String = "",
    val profile: ProfileDto? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
    val self: Boolean = false,
) {
    
    fun createTimeMillis(): Long = parseServerDateMillis(createTime)

    
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
            value = decodeHistoryValue(o["value"]),
        )

        

        private fun decodeHistoryValue(el: JsonElement?): JsonElement? {
            val primitive = el as? JsonPrimitive ?: return el
            if (!primitive.isString) return el
            val content = primitive.content
            if (content.length < 2) return el
            val decoded = runCatching { Json.parseToJsonElement(content) }.getOrNull() ?: return el
            
            
            return when {
                decoded is JsonObject || decoded is JsonArray -> {
                    BadgerLog.d("SyncChange", "decodeHistoryValue: 字符串化 JSON 已二次解码 len=${content.length}")
                    decoded
                }
                decoded is JsonPrimitive && decoded.isString -> decoded
                else -> el
            }
        }
    }
}

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

fun parseServerDateMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val s = raw.trim()
    s.toLongOrNull()?.let {
        
        return if (it in 1_000_000_000L..99_999_999_999L) it * 1000L else it
    }
    s.toDoubleOrNull()?.let { d ->
        return if (d in 1_000_000_000.0..99_999_999_999.0) (d * 1000.0).toLong() else d.toLong()
    }
    
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
