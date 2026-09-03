package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 自建短链 endpoints（`Badger-Server/docs/api-handover.md` §8）。
 *
 * 与 short.io 代理（`/api/proxy/shortio`）不同，本域走 `/api/shortlinks/` 路径，
 * 由服务端自行管理短链存储（不依赖外部 short.io API key）。
 *
 * ⚠️ 路径尾斜杠 `/api/shortlinks/` 必须带，否则 Javalin 严格匹配 404。
 *
 * - `GET /api/shortlinks/config` — 短链配置快照（功能开关 + 用户选择 + key 是否已设）
 * - `GET /api/shortlinks/` — 我的短链列表
 * - `POST /api/shortlinks/` — 创建短链 `{ originalURL, code? }`
 * - `PUT /api/shortlinks/{uuid}` — 修改短链 `{ originalURL?, code? }`
 * - `DELETE /api/shortlinks/{uuid}` — 删除短链
 */
internal class ServerShortLinkApi(private val core: ApiCore) {

    /** GET /api/shortlinks/config — 短链配置快照。 */
    fun getConfig(): ShortLinkConfig {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] shortlinks.config")
        return core.execute(core.buildRequest("GET", "/api/shortlinks/config").build())
            .unwrapApiResult("shortlinks.config", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    Log.w(TAG, "[$tag] config: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult ShortLinkConfig(false, false, false)
                }
                ShortLinkConfig.from(obj)
            }
    }

    /** GET /api/shortlinks/ — 我的短链列表（新→旧）。 */
    fun listLinks(): List<ServerShortLink> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] shortlinks.list")
        return core.execute(core.buildRequest("GET", "/api/shortlinks/").build())
            .unwrapApiResult("shortlinks.list", tag) { data ->
                val arr = (data as? JsonObject)?.get("links") as? JsonArray
                    ?: data as? JsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] list: expected array or {links:[...]}, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    ServerShortLink.from(o)
                }
            }
    }

    /**
     * POST /api/shortlinks/ — 创建短链。
     *
     * @return 服务端分配的 uuid。
     */
    fun createLink(originalURL: String, code: String? = null): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("originalURL", originalURL)
            code?.takeIf { it.isNotBlank() }?.let { put("code", it) }
        }
        Log.d(TAG, "[$tag] shortlinks.create: url=${originalURL.take(80)} code=${code?.take(20)}")
        return core.execute(core.buildRequest("POST", "/api/shortlinks/", payload.toString()).build())
            .unwrapApiResult("shortlinks.create", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else ""
                if (uuid.isBlank()) throw ApiException(0, "shortlinks.create missing uuid", "shortlinks.create")
                uuid
            }
    }

    /** PUT /api/shortlinks/{uuid} — 修改短链 `{ originalURL?, code? }`。 */
    fun updateLink(uuid: String, originalURL: String? = null, code: String? = null) {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            originalURL?.let { put("originalURL", it) }
            code?.takeIf { it.isNotBlank() }?.let { put("code", it) }
        }
        Log.d(TAG, "[$tag] shortlinks.update: uuid=${uuid.take(8)}")
        core.execute(core.buildRequest("PUT", "/api/shortlinks/$uuid", payload.toString()).build())
            .unwrapApiResult("shortlinks.update", tag) { /* data: null */ }
    }

    /** DELETE /api/shortlinks/{uuid} — 删除短链；404 幂等成功。 */
    fun deleteLink(uuid: String): Boolean {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] shortlinks.delete: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.buildRequest("DELETE", "/api/shortlinks/$uuid").build())
                .unwrapApiResult("shortlinks.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                Log.d(TAG, "[$tag] delete 404 idempotent")
                true
            } else throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG

        fun validateUuid(uuid: String) {
            // [安全加固]: 强制 UUID 格式，阻断路径注入（/ ? # .. % 等）
            require(UUID_REGEX.matches(uuid)) {
                "invalid shortlink uuid: must be UUID format"
            }
        }

        private val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
