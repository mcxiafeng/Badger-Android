package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * [Phase 3] Person CRUD endpoints（新 Java `/api` 契约）。
 *
 * 与旧 Go `/v1/contacts` 差异（`Badger-Server/docs/api-handover.md` §4.2）：
 * - 路径 `/v1/contacts` → `/api/user/persons`；无版本号、无 If-Match；
 * - 响应一律 `{code,message,data}` ApiResult 壳，本类全部走 [Response.unwrapApiResult]；
 * - 数据模型 `Contact{id,serverId,version,...}` → `Person{uuid,name,profile,createTime,updateTime,self}`，
 *   profile 为嵌套 Profile 对象（camelCase）；
 * - 创建支持**客户端 uuid 幂等重放**：携带同一 uuid 重试返回既有行（已存在属本人则成功，
 *   撞他人 409）；selfPerson 由注册创建，不经过本端点；
 * - DELETE 禁删 selfPerson（400 由服务端守卫，客户端不做本地拦截）；
 * - DELETE 404 幂等保留（服务端已删视为成功）。
 */
class PersonApi(private val core: ApiCore) {

    /** GET /api/user/persons — 全部人物（含 selfPerson，[PersonDto.self] 标记）。 */
    fun listPersons(): List<PersonDto> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] listPersons")
        return core.execute(core.buildRequest("GET", "/api/user/persons").build())
            .unwrapApiResult("persons.list", tag) { data ->
                if (!data.isJsonArray) {
                    Log.w(TAG, "[$tag] listPersons: expected array, got ${data.javaClass.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                val rows = data.asJsonArray.mapNotNull { el ->
                    runCatching { PersonDto.from(el.asJsonObject) }.getOrNull()
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
                if (!data.isJsonObject) {
                    throw ApiException(0, data.toString().take(200), "persons.get data not object")
                }
                PersonDto.from(data.asJsonObject)
            }
    }

    /**
     * POST /api/user/persons — 新建人物 `{ name, profile?, uuid? }`。
     *
     * [clientUuid] 为客户端幂等重放键：首次创建时客户端生成 uuid 携带，超时/重试重放
     * 同一 uuid → 服务端返回既有行（不产生克隆体）；撞他人 → 409 [ApiException]。
     *
     * @return 服务端 person uuid（幂等重放时即既有行 uuid）。
     */
    fun createPerson(name: String, profile: ProfileDto?, clientUuid: String): String {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            addProperty("name", name)
            clientUuid.takeIf { it.isNotBlank() }?.let { addProperty("uuid", it) }
            profile?.let { add("profile", it.toJsonObject()) }
        }
        // [修复防御]: 缓存序列化结果，避免 payload.toString() 被调用两次（日志 + 请求）
        val payloadStr = payload.toString()
        Log.d(TAG, "[$tag] createPerson: name=$name uuid=${clientUuid.take(8)} bytes=${payloadStr.length}")
        return core.execute(core.buildRequest("POST", "/api/user/persons", payloadStr).build())
            .unwrapApiResult("persons.create", tag) { data ->
                val uuid = if (data.isJsonObject) {
                    stringOrNull(data.asJsonObject, "uuid").orEmpty()
                } else {
                    Log.w(TAG, "[$tag] createPerson: data not object, fallback to clientUuid")
                    clientUuid
                }
                if (uuid.isBlank()) throw ApiException(0, "createPerson missing uuid", "persons.create")
                Log.d(TAG, "[$tag] createPerson OK: uuid=${uuid.take(8)}")
                uuid
            }
    }

    /**
     * PUT /api/user/persons/{uuid} — 改人物 `{ name?, profile? }`，仅传字段更新。
     * 清空字段传空串（服务端 SQLManager 跳过 null）。
     */
    fun updatePerson(uuid: String, name: String?, profile: ProfileDto?) {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            name?.let { addProperty("name", it) }
            profile?.let { add("profile", it.toJsonObject()) }
        }
        // [修复防御]: 缓存序列化结果，避免 payload.toString() 被调用两次（日志 + 请求）
        val payloadStr = payload.toString()
        Log.d(TAG, "[$tag] updatePerson: uuid=${uuid.take(8)} bytes=${payloadStr.length}")
        core.execute(core.buildRequest("PUT", "/api/user/persons/$uuid", payloadStr).build())
            .unwrapApiResult("persons.update", tag) { /* data: null */ }
    }

    /**
     * DELETE /api/user/persons/{uuid}
     *
     * - 2xx → 删除成功
     * - 404 → 服务端已删（幂等成功）
     * - 400 → selfPerson 禁删（服务端守卫，原样抛出）
     */
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

    /**
     * POST /api/user/persons/{uuid}/merge — 合并人物 `{ merged_ids: [...] }`。
     * target 保留（字段不动），merged 行由服务端删除并级联清 personMembers。
     * 撞 target 自身 / selfPerson / 他人 / 不存在 → 服务端整批拒绝（400/403/404）。
     *
     * @return target uuid（`data:{uuid}`）。
     */
    fun mergePersons(targetUuid: String, mergedIds: List<String>): String {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            val arr = JsonArray()
            mergedIds.forEach { arr.add(it) }
            add("merged_ids", arr)
        }
        Log.d(TAG, "[$tag] mergePersons: target=${targetUuid.take(8)} merged=${mergedIds.size}")
        return core.execute(core.buildRequest("POST", "/api/user/persons/$targetUuid/merge", payload.toString()).build())
            .unwrapApiResult("persons.merge", tag) { data ->
                val uuid = if (data.isJsonObject) {
                    stringOrNull(data.asJsonObject, "uuid").orEmpty()
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

/**
 * 服务端 Person 行：`{uuid, name, profile, createTime, updateTime, self}`。
 *
 * [createTime]/[updateTime] 服务端 Date 序列化为字符串（fastjson2 默认格式）；
 * 客户端需要 epoch millis 时用 [parseServerDateMillis] 解析（见 [PersonDto.timeMillis]）。
 * [self]=true 表示该行是当前用户的身份档案（禁删）。
 */
data class PersonDto(
    val uuid: String,
    val name: String,
    val profile: ProfileDto?,
    val createTime: String?,
    val updateTime: String?,
    val self: Boolean,
) {
    /** 服务端 createTime → epoch millis；解析失败回退 0（不炸调用方）。 */
    fun createTimeMillis(): Long = parseServerDateMillis(createTime)

    /** 服务端 updateTime → epoch millis；解析失败回退 0。 */
    fun updateTimeMillis(): Long = parseServerDateMillis(updateTime)

    companion object {
        fun from(o: JsonObject): PersonDto = PersonDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            profile = o.getAsJsonObject("profile")?.let { ProfileDto.from(it) },
            createTime = stringOrNull(o, "createTime"),
            updateTime = stringOrNull(o, "updateTime"),
            self = o.get("self")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
        )
    }
}

/**
 * 服务端 Person 的嵌套 Profile：`{sex, avatarURL, backgroundURL, description, country,
 * region, birthday, contactMap(Map<String,String>), extra(Map<String,Map<String,Object>>)}`。
 *
 * [extra] 保持原始 [JsonObject] 透传（类型保真、无丢失），客户端只读接触点通常用
 * [contactMap]（平台 key → value）。
 */
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
    fun toJsonObject(): JsonObject = JsonObject().apply {
        sex?.let { addProperty("sex", it) }
        avatarURL?.let { addProperty("avatarURL", it) }
        backgroundURL?.let { addProperty("backgroundURL", it) }
        description?.let { addProperty("description", it) }
        country?.let { addProperty("country", it) }
        region?.let { addProperty("region", it) }
        birthday?.let { addProperty("birthday", it) }
        if (contactMap.isNotEmpty()) {
            val m = JsonObject()
            contactMap.forEach { (k, v) -> m.addProperty(k, v) }
            add("contactMap", m)
        }
        extra?.let { add("extra", it) }
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
            contactMap = parseStringMap(o.getAsJsonObject("contactMap")),
            extra = o.getAsJsonObject("extra"),
        )
    }
}

/**
 * 服务端 owner 域变更日志快照：Tag 行 `{uuid, name, colorHash, personMembers, createTime}`，
 * Collection 行 `{uuid, name, description, backgroundURL, personMembers, createTime}`。
 */
data class TagDto(
    val uuid: String,
    val name: String,
    val colorHash: String?,
    val personMembers: List<String>,
    val createTime: String?,
) {
    companion object {
        fun from(o: JsonObject): TagDto = TagDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            colorHash = stringOrNull(o, "colorHash"),
            personMembers = parseStringArray(o.getAsJsonArray("personMembers")),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

data class CollectionDto(
    val uuid: String,
    val name: String,
    val description: String?,
    val backgroundURL: String?,
    val personMembers: List<String>,
    val createTime: String?,
) {
    companion object {
        fun from(o: JsonObject): CollectionDto = CollectionDto(
            uuid = stringOrNull(o, "uuid").orEmpty(),
            name = stringOrNull(o, "name").orEmpty(),
            description = stringOrNull(o, "description"),
            backgroundURL = stringOrNull(o, "backgroundURL"),
            personMembers = parseStringArray(o.getAsJsonArray("personMembers")),
            createTime = stringOrNull(o, "createTime"),
        )
    }
}

/**
 * 服务端 UserHistory 变更行 → 客户端增量重放单元（`GET /api/user/sync` 的 `changes` 元素）。
 *
 * 字段对齐 `core/user/UserHistory.java`（fastjson2 camelCase 序列化）：
 * - [type]：ADD=完整快照（[value] 为对象 JSON）/ UPDATE=字段新值（[value] 为字段值 JSON 文本）/
 *   REMOVE=删除（仅 [objectId] 有效）；
 * - [objectName]：被变更的表名（`Person`/`Collection`/`Tag`/`Device`/`UserSettings`…）；
 * - [fieldName]：UPDATE 专用，被修改字段的 Java 字段名（如 `name`/`profile`/`updateTime`）。
 */
data class SyncChange(
    val version: Long,
    val type: String,
    val objectName: String,
    val objectId: String?,
    val fieldName: String?,
    val value: JsonElement?,
) {
    companion object {
        fun from(o: JsonObject): SyncChange = SyncChange(
            version = o.get("version")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            type = stringOrNull(o, "type").orEmpty(),
            objectName = stringOrNull(o, "objectName").orEmpty(),
            objectId = stringOrNull(o, "objectId"),
            fieldName = stringOrNull(o, "fieldName"),
            value = o.get("value"),
        )
    }
}

/** `GET /api/user/sync?since=` 增量拉取结果：[version] 为下一轮 since，[hasMore] 提示续拉。 */
data class SyncPage(
    val version: Long,
    val changes: List<SyncChange>,
    val hasMore: Boolean,
) {
    companion object {
        fun from(o: JsonObject): SyncPage = SyncPage(
            version = o.get("version")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            changes = o.getAsJsonArray("changes")?.mapNotNull { el ->
                runCatching { SyncChange.from(el.asJsonObject) }.getOrNull()
            } ?: emptyList(),
            hasMore = o.get("hasMore")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
        )
    }
}

/** 把服务端 Date 字符串（fastjson2 默认格式）或 epoch 数值解析为 epoch millis；失败回退 0。 */
internal fun parseServerDateMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    raw.trim().let { s ->
        s.toLongOrNull()?.let {
            // 秒级时间戳（< 1e12）转毫秒，毫秒级原样返回
            return if (it in 1_000_000_000L..99_999_999_999L) it * 1000L else it
        }
        s.toDoubleOrNull()?.let { return (it * 1000.0).toLong() }
    }
    // fastjson2 默认 Date 格式: yyyy-MM-dd HH:mm:ss；兼容 ISO-8601
    // [修复防御]: 'Z' 是 Char, 与 "" (String) 混用无重载, 改用 "Z" String 重载
    val cleaned = raw.replace('T', ' ').replace("Z", "")
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fmt.isLenient = false
        fmt.parse(cleaned)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun parseStringMap(o: JsonObject?): Map<String, String> {
    if (o == null) return emptyMap()
    return o.entrySet().associate { (k, v) ->
        k to (if (v.isJsonNull) "" else v.asString)
    }
}

private fun parseStringArray(o: com.google.gson.JsonArray?): List<String> {
    if (o == null) return emptyList()
    return o.mapNotNull { it.takeIfString() }
}
