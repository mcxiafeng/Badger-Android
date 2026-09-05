package top.mcxiafeng.badger.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

/** Person CRUD endpoints（新 Java /api 契约）。 */
class PersonApi(private val core: ApiCore) {

    /** GET /api/user/persons — 全部人物（含 selfPerson，[PersonDto.self] 标记）。 */
    fun listPersons(): List<PersonDto> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] listPersons")
        return core.execute(core.buildRequest("GET", "/api/user/persons").build())
            .unwrapApiResult("persons.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] listPersons: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                val rows = arr.mapNotNull { el ->
                    runCatching { PersonDto.from(el as JsonObject) }.getOrNull()
                }
                BadgerLog.d(TAG, "[$tag] listPersons OK: rows=${rows.size}")
                rows
            }
    }

    /** GET /api/user/persons/{uuid} — 单查（详情页）。不存在 404、撞他人 403 由服务端守卫。 */
    fun getPerson(uuid: String): PersonDto {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] getPerson: uuid=$uuid")
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
        BadgerLog.d(TAG, "[$tag] createPerson: name=$name uuid=${clientUuid.take(8)} bytes=${payloadStr.length}")
        return core.execute(core.buildRequest("POST", "/api/user/persons", payloadStr).build())
            .unwrapApiResult("persons.create", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else {
                    BadgerLog.w(TAG, "[$tag] createPerson: data not object, fallback to clientUuid")
                    clientUuid
                }
                if (uuid.isBlank()) throw ApiException(0, "createPerson missing uuid", "persons.create")
                BadgerLog.d(TAG, "[$tag] createPerson OK: uuid=${uuid.take(8)}")
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
        BadgerLog.d(TAG, "[$tag] updatePerson: uuid=${uuid.take(8)} bytes=${payloadStr.length}")
        core.execute(core.buildRequest("PUT", "/api/user/persons/$uuid", payloadStr).build())
            .unwrapApiResult("persons.update", tag) { /* data: null */ }
    }

    /** DELETE /api/user/persons/{uuid}，404 视为幂等成功。 */
    fun deletePerson(uuid: String): Boolean {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] deletePerson: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/persons/$uuid").build())
                .unwrapApiResult("persons.delete", tag) { /* data: null */ true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.w(TAG, "[$tag] deletePerson 404: server already removed, idempotent success")
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
        BadgerLog.d(TAG, "[$tag] mergePersons: target=${targetUuid.take(8)} merged=${mergedIds.size}")
        return core.execute(core.buildRequest("POST", "/api/user/persons/$targetUuid/merge", payload.toString()).build())
            .unwrapApiResult("persons.merge", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else {
                    BadgerLog.w(TAG, "[$tag] mergePersons: data not object, fallback to targetUuid")
                    targetUuid
                }
                uuid
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
