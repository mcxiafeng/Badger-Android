package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Person CRUD endpoints（新 Java /api 契约）。 */
class PersonApi(private val core: ApiCore) {

    /** GET /api/user/persons — 全部人物（含 selfPerson，[PersonDto.self] 标记）。 */
    fun listPersons(): List<PersonDto> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] listPersons")
        return core.execute(core.buildRequest("GET", "/api/user/persons").build())
            .unwrapApiResult("persons.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] listPersons: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                val rows = arr.mapNotNull { el ->
                    runCatching { PersonDto.from(el as JsonObject) }.getOrNull()
                }
                Log.d(TAG, "[$tag] listPersons OK: rows=${rows.size}")
                rows
            }
    }

    /** GET /api/user/persons/{uuid} — 单查（详情页）。不存在 404、撞他人 403 由服务端守卫。 */
    fun getPerson(uuid: String): PersonDto {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] getPerson: uuid=$uuid")
        return core.execute(core.buildRequest("GET", "/api/user/persons/$uuid").build())
            .unwrapApiResult("persons.get", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    throw ApiException(0, data.toString().take(200), "persons.get data not object")
                }
                PersonDto.from(obj)
            }
    }

    /** POST /api/user/persons — 新建人物，clientUuid 为幂等重放键。 */
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("name", name)
            clientUuid.takeIf { it.isNotBlank() }?.let { put("uuid", it) }
            profile?.let { put("profile", it.toJsonObject()) }
        }
    // [修复防御]: 缓存序列化结果，避免 payload.toString() 被调用两次（日志 + 请求）
        val payloadStr = payload.toString()
        Log.d(TAG, "[$tag] createPerson: name=$name uuid=${clientUuid.take(8)} bytes=${payloadStr.length}")
        return core.execute(core.buildRequest("POST", "/api/user/persons", payloadStr).build())
            .unwrapApiResult("persons.create", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else {
                    Log.w(TAG, "[$tag] createPerson: data not object, fallback to clientUuid")
                    clientUuid
                }
                if (uuid.isBlank()) throw ApiException(0, "createPerson missing uuid", "persons.create")
                Log.d(TAG, "[$tag] createPerson OK: uuid=${uuid.take(8)}")
                uuid
            }
    }

    /** PUT /api/user/persons/{uuid} — 改人物。 */
    fun updatePerson(uuid: String, name: String?, profile: ProfileDto?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            profile?.let { put("profile", it.toJsonObject()) }
        }
        // [修复防御]: 缓存序列化结果，避免 payload.toString() 被调用两次（日志 + 请求）
        val payloadStr = payload.toString()
        Log.d(TAG, "[$tag] updatePerson: uuid=${uuid.take(8)} bytes=${payloadStr.length}")
        core.execute(core.buildRequest("PUT", "/api/user/persons/$uuid", payloadStr).build())
            .unwrapApiResult("persons.update", tag) { /* data: null */ }
    }

    /** DELETE /api/user/persons/{uuid}，404 视为幂等成功。 */
    fun deletePerson(uuid: String): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deletePerson: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/persons/$uuid").build())
                .unwrapApiResult("persons.delete", tag) { /* data: null */ true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deletePerson 404: server already removed, idempotent success")
                true
            } else throw e
        }
    }

    /** POST /api/user/persons/{uuid}/merge — 合并人物。 */
    fun mergePersons(targetUuid: String, mergedIds: List<String>): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("merged_ids", JsonArray(mergedIds.map { JsonPrimitive(it) }))
        }
        Log.d(TAG, "[$tag] mergePersons: target=${targetUuid.take(8)} merged=${mergedIds.size}")
        return core.execute(core.buildRequest("POST", "/api/user/persons/$targetUuid/merge", payload.toString()).build())
            .unwrapApiResult("persons.merge", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else {
                    Log.w(TAG, "[$tag] mergePersons: data not object, fallback to targetUuid")
                    targetUuid
                }
                uuid
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}

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

/** 服务端 Date 字符串或 epoch 数值解析为 epoch millis；失败回退 0。 */
internal fun parseServerDateMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    raw.trim().let { s ->
        s.toLongOrNull()?.let {
            // 秒级时间戳转毫秒，毫秒级原样返回
            return if (it in 1_000_000_000L..99_999_999_999L) it * 1000L else it
        }
        s.toDoubleOrNull()?.let { d ->
            return if (d in 1_000_000_000.0..99_999_999_999.0) (d * 1000.0).toLong() else d.toLong()
        }
    }
    // fastjson2 默认格式: yyyy-MM-dd HH:mm:ss
    val cleaned = raw.replace('T', ' ').replace("Z", "")
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fmt.isLenient = false
        fmt.parse(cleaned)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
