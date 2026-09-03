package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [Phase 3] Profile / Tag / Collection 域 HTTP facade（新 Java `/api` 契约）。
 *
 * 与旧 Go `/v1` 差异（`Badger-Server/docs/api-handover.md` §4.1/§4.4/§4.5）：
 * - 路径全部迁到 `/api/user/` 域（Profile/Tag/Collection 子路径），ApiResult 壳，本类全部走 [Response.unwrapApiResult]；
 * - Profile：`PATCH /api/auth/me` → `PUT /api/user/profile`，body `{ name?, profile? }`
 *   （嵌套 Profile 对象，不再用 display_name/bio/avatar_url/platforms_json 平铺字段）；
 * - Tag/Collection：`id:Long` → `uuid:String`，`color` → `colorHash`，新增 `personMembers`；
 *   collection 成员走子接口（不随 PUT 基础字段传）；
 * - Tag 名 owner 域内唯一（重名 400 由服务端守卫）。
 *
 * [修复防御]：成员子接口是独立 POST/DELETE（服务端按 personUuid 归属校验 + 幂等），
 * 客户端批量改成员时逐条调用，失败仅污染该条（与 merge 的整批拒绝不同）。
 */
class V2DomainApi(private val core: ApiCore) {

    // ============ Profile (我的名片) ============

    /** PUT /api/user/profile `{ name?, profile? }` — 改 selfPerson 的 name/profile，仅传字段更新。 */
    fun patchProfile(name: String?, profile: ProfileDto?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            profile?.let { put("profile", it.toJsonObject()) }
        }
        Log.d(TAG, "[$tag] patchProfile: bytes=${payload.toString().length}")
        core.execute(core.buildRequest("PUT", "/api/user/profile", payload.toString()).build())
            .unwrapApiResult("profile.patch", tag) { /* data: null */ }
    }

    /** GET /api/user/profile — selfPerson 资料：`data: { name, displayName, profile }`。 */
    fun getProfile(): UserProfileResponse {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] getProfile")
        return core.execute(core.buildRequest("GET", "/api/user/profile").build())
            .unwrapApiResult("profile.get", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    Log.w(TAG, "[$tag] getProfile: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult UserProfileResponse(null, null, null)
                }
                UserProfileResponse.from(obj)
            }
    }

    // ============ Tags ============

    /** GET /api/user/tags — 全部标签（含 colorHash/personMembers）。 */
    fun listTags(): List<TagDto> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] listTags")
        return core.execute(core.buildRequest("GET", "/api/user/tags").build())
            .unwrapApiResult("tags.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] listTags: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el -> runCatching { TagDto.from(el as JsonObject) }.getOrNull() }
            }
    }

    /**
     * POST /api/user/tags `{ name, colorHash?, personMembers?, uuid? }` → `data:{uuid}`。
     *
     * [T14 选项 C] [uuid] 为客户端幂等键（Person 同形状携带）。服务端当前契约不认识该字段：
     * 忽略 → 调用方用返回 uuid 覆盖本地；400 → 调用方去 uuid 重试一次。
     */
    fun createTag(name: String, colorHash: String?, personMembers: List<String>?, uuid: String? = null): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("name", name)
            colorHash?.takeIf { it.isNotBlank() }?.let { put("colorHash", it) }
            personMembers?.takeIf { it.isNotEmpty() }?.let { put("personMembers", toStrArr(it)) }
            uuid?.let { put("uuid", it) }
        }
        Log.d(TAG, "[$tag] createTag: name=$name members=${personMembers?.size ?: 0} uuid=${uuid?.take(8)}")
        return core.execute(core.buildRequest("POST", "/api/user/tags", payload.toString()).build())
            .unwrapApiResult("tags.create", tag) { data ->
                uuidFromData(data, tag, "tags.create")
            }
    }

    /** PUT /api/user/tags/{uuid} `{ name?, colorHash? }` — 仅传字段更新。 */
    fun patchTag(uuid: String, name: String?, colorHash: String?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            colorHash?.let { put("colorHash", it) }
        }
        Log.d(TAG, "[$tag] patchTag: uuid=${uuid.take(8)}")
        core.execute(core.buildRequest("PUT", "/api/user/tags/$uuid", payload.toString()).build())
            .unwrapApiResult("tags.patch", tag) { /* data: null */ }
    }

    /** DELETE /api/user/tags/{uuid} — 删除；404 幂等成功。 */
    fun deleteTag(uuid: String): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteTag: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/tags/$uuid").build())
                .unwrapApiResult("tags.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteTag 404: server already removed, idempotent success")
                true
            } else throw e
        }
    }

    /** POST /api/user/tags/{uuid}/members/{personUuid} — 加成员（服务端归属校验 + 幂等）。 */
    fun addTagMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] addTagMember: tag=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.buildRequest("POST", "/api/user/tags/$uuid/members/$personUuid").build())
            .unwrapApiResult("tags.member.add", tag) { /* data: null */ }
    }

    /** DELETE /api/user/tags/{uuid}/members/{personUuid} — 移除成员（removeIf 全量清除）。 */
    fun removeTagMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] removeTagMember: tag=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.buildRequest("DELETE", "/api/user/tags/$uuid/members/$personUuid").build())
            .unwrapApiResult("tags.member.remove", tag) { /* data: null */ }
    }

    // ============ Collections ============

    /** GET /api/user/collections — 全部名片夹（含 personMembers）。 */
    fun listCollections(): List<CollectionDto> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] listCollections")
        return core.execute(core.buildRequest("GET", "/api/user/collections").build())
            .unwrapApiResult("collections.list", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] listCollections: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el -> runCatching { CollectionDto.from(el as JsonObject) }.getOrNull() }
            }
    }

    /**
     * POST /api/user/collections `{ name, description?, backgroundURL?, personMembers?, uuid? }` → `data:{uuid}`。
     *
     * [T14 选项 C] [uuid] 为客户端幂等键（Person 同形状携带），契约缺口处理同 [createTag]。
     */
    fun createCollection(
        name: String,
        description: String?,
        backgroundURL: String?,
        personMembers: List<String>?,
        uuid: String? = null,
    ): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            backgroundURL?.let { put("backgroundURL", it) }
            personMembers?.takeIf { it.isNotEmpty() }?.let { put("personMembers", toStrArr(it)) }
            uuid?.let { put("uuid", it) }
        }
        Log.d(TAG, "[$tag] createCollection: name=$name members=${personMembers?.size ?: 0} uuid=${uuid?.take(8)}")
        return core.execute(core.buildRequest("POST", "/api/user/collections", payload.toString()).build())
            .unwrapApiResult("collections.create", tag) { data ->
                uuidFromData(data, tag, "collections.create")
            }
    }

    /** PUT /api/user/collections/{uuid} `{ name?, description?, backgroundURL? }` — 成员走子接口。 */
    fun patchCollection(uuid: String, name: String?, description: String?, backgroundURL: String?) {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            description?.let { put("description", it) }
            backgroundURL?.let { put("backgroundURL", it) }
        }
        Log.d(TAG, "[$tag] patchCollection: uuid=${uuid.take(8)}")
        core.execute(core.buildRequest("PUT", "/api/user/collections/$uuid", payload.toString()).build())
            .unwrapApiResult("collections.patch", tag) { /* data: null */ }
    }

    /** DELETE /api/user/collections/{uuid} — 删除；404 幂等成功。 */
    fun deleteCollection(uuid: String): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteCollection: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/user/collections/$uuid").build())
                .unwrapApiResult("collections.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteCollection 404: server already removed, idempotent success")
                true
            } else throw e
        }
    }

    /** POST /api/user/collections/{uuid}/members/{personUuid} — 加成员（归属校验 + 幂等）。 */
    fun addCollectionMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] addCollectionMember: col=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.buildRequest("POST", "/api/user/collections/$uuid/members/$personUuid").build())
            .unwrapApiResult("collections.member.add", tag) { /* data: null */ }
    }

    /** DELETE /api/user/collections/{uuid}/members/{personUuid} — 移除成员（removeIf 全量清除）。 */
    fun removeCollectionMember(uuid: String, personUuid: String) {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] removeCollectionMember: col=${uuid.take(8)} person=${personUuid.take(8)}")
        core.execute(core.buildRequest("DELETE", "/api/user/collections/$uuid/members/$personUuid").build())
            .unwrapApiResult("collections.member.remove", tag) { /* data: null */ }
    }

    // ============ 便捷工具 ============

    private fun toStrArr(list: List<String>): JsonArray = JsonArray(list.map { JsonPrimitive(it) })

    private fun uuidFromData(data: JsonElement, tag: String, what: String): String {
        val uuid = if (data is JsonObject) {
            stringOrNull(data, "uuid").orEmpty()
        } else {
            Log.w(ApiCore.TAG, "[$tag] $what: data not object, uuid empty")
            ""
        }
        if (uuid.isBlank()) throw ApiException(0, "$what missing uuid", what)
        return uuid
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}
