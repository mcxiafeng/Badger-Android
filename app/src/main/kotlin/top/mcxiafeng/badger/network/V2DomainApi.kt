package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * [V2-P12] Profile / Tag / Collection 域 HTTP facade。
 *
 * 与 [ContactApi] 一样持有 [ApiCore] 走统一 HTTP 通道,把服务端 V2 域
 * `/api/auth/me` (profile) + `/v1/tags` + `/v1/collections` 暴露成强类型方法,
 * 给 [PendingUploadExecutor] 消费 op 队列时调用。
 *
 * 选 POST/PATCH/DELETE 而不是写 envelope 是为了**直接复用**服务端 §3 路由表
 * (router.go L191-L238 已经挂好),客户端不需要服务端再开新端点。
 *
 * 401 由 [ApiCore] 抛出 [ApiException] → PendingUploadExecutor 收到后按 FAILED_RETRY
 * 退避,不需要客户端自己重试 refresh token。
 */
class V2DomainApi(private val core: ApiCore) {

    // ============ Profile (我的名片) ============

    /**
     * PATCH /api/auth/me
     *
     * [displayName] / [bio] / [avatarUrl] / [platformsJson] 任意子集;null 表示不动该字段。
     * 服务端只会修改 schema 显式声明的字段,客户端解析 platformsJson 失败时整体兜底 500。
     */
    fun patchMe(
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        platformsJson: String? = null,
    ): JsonObject {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            displayName?.let { addProperty("display_name", it) }
            bio?.let { addProperty("bio", it) }
            avatarUrl?.let { addProperty("avatar_url", it) }
            platformsJson?.let { addProperty("platforms_json", it) }
        }
        Log.d(TAG, "[$tag] patchMe: bytes=${payload.toString().length}")
        return core.execute(core.buildRequest("PATCH", "/api/auth/me", payload.toString()).build())
            .useNot2xxOrOk("auth.me.patch", tag) { resp ->
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                Log.d(TAG, "[$tag] patchMe OK: code=${resp.code}")
                obj
            }
    }

    // ============ Tags ============

    /** POST /v1/tags  新建标签。 */
    fun createTag(name: String, color: String, pinyinInitial: String): JsonObject {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            addProperty("name", name)
            addProperty("color", color)
            addProperty("pinyin_initial", pinyinInitial)
        }
        Log.d(TAG, "[$tag] createTag: name=$name")
        return core.execute(core.buildRequest("POST", "/api/tags", payload.toString()).build())
            .useNot2xxOrOk("tags.create", tag) { resp ->
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
    }

    /** PATCH /v1/tags/{id}  更新(name / color)。 */
    fun patchTag(id: Long, name: String? = null, color: String? = null): JsonObject {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            name?.let { addProperty("name", it) }
            color?.let { addProperty("color", it) }
        }
        Log.d(TAG, "[$tag] patchTag: id=$id")
        return core.execute(core.buildRequest("PATCH", "/api/tags/$id", payload.toString()).build())
            .useNot2xxOrOk("tags.patch", tag) { resp ->
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
    }

    /** DELETE /v1/tags/{id}  删除标签。 */
    fun deleteTag(id: Long): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteTag: id=$id")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/tags/$id", null).build())
                .useNot2xxOrOk("tags.delete", tag) { resp ->
                    Log.d(TAG, "[$tag] deleteTag OK: code=${resp.code}")
                    true
                }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteTag 404: server already removed, treating as idempotent success")
                true
            } else throw e
        }
    }

    // ============ Collections ============

    /** POST /v1/collections  新建名片夹。 */
    fun createCollection(
        name: String,
        color: String? = null,
        backgroundImagePath: String? = null,
    ): JsonObject {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            addProperty("name", name)
            color?.let { addProperty("color", it) }
            backgroundImagePath?.let { addProperty("background_image_path", it) }
        }
        Log.d(TAG, "[$tag] createCollection: name=$name")
        return core.execute(core.buildRequest("POST", "/api/collections", payload.toString()).build())
            .useNot2xxOrOk("collections.create", tag) { resp ->
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
    }

    /** PATCH /v1/collections/{id}  更新名片夹(name / color / background)。 */
    fun patchCollection(
        id: Long,
        name: String? = null,
        color: String? = null,
        backgroundImagePath: String? = null,
    ): JsonObject {
        val tag = core.nextCallTag()
        val payload = JsonObject().apply {
            name?.let { addProperty("name", it) }
            color?.let { addProperty("color", it) }
            backgroundImagePath?.let { addProperty("background_image_path", it) }
        }
        Log.d(TAG, "[$tag] patchCollection: id=$id")
        return core.execute(core.buildRequest("PATCH", "/api/collections/$id", payload.toString()).build())
            .useNot2xxOrOk("collections.patch", tag) { resp ->
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
    }

    /** DELETE /v1/collections/{id}  删除名片夹。 */
    fun deleteCollection(id: Long): Boolean {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] deleteCollection: id=$id")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/collections/$id", null).build())
                .useNot2xxOrOk("collections.delete", tag) { resp ->
                    Log.d(TAG, "[$tag] deleteCollection OK: code=${resp.code}")
                    true
                }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.w(TAG, "[$tag] deleteCollection 404: server already removed, treating as idempotent success")
                true
            } else throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}

/**
 * ServerApiTypes 中用于 V2 域响应解析的辅助函数。
 *
 * TagPatchResponse / CollectionPatchResponse 复用 `JsonObject`,调用方按需
 * 取 `id` / `version` / `server_id`。这里集中提供"取整数 id"的便利函数,
 * 避免调用方重复写 `obj.get("id")?.asLong ?: 0L`。
 */
internal fun JsonObject.optLong(name: String, default: Long = 0L): Long =
    runCatching { get(name)?.asLong ?: default }.getOrDefault(default)

/**
 * 序列化 platformsJson 时复用 [ContactMapper.encodePlatformsMap] 的反向操作。
 * 这里单独提供"对象转 JSON 字符串"以避免 ContactMapper 内部循环依赖。
 */
internal fun jsonOf(map: Map<String, Any?>): JsonObject {
    val obj = JsonObject()
    map.forEach { (k, v) ->
        when (v) {
            null -> obj.add(k, com.google.gson.JsonNull.INSTANCE)
            is String -> obj.addProperty(k, v)
            is Number -> obj.addProperty(k, v)
            is Boolean -> obj.addProperty(k, v)
            is JsonObject -> obj.add(k, v)
            is JsonArray -> obj.add(k, v)
            else -> obj.addProperty(k, v.toString())
        }
    }
    return obj
}

private val _unused = JsonArray()  // 防止 JsonArray import 被 IDE 自动删除