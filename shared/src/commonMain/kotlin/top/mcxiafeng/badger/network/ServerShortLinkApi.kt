package top.mcxiafeng.badger.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.mcxiafeng.badger.utils.BadgerLog

internal class ServerShortLinkApi(private val core: ApiCore) {

    
    fun getConfig(): ShortLinkConfig {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] shortlinks.config")
        return core.execute(core.request("GET", "/api/shortlinks/config"))
            .unwrapApiResult("shortlinks.config", tag) { data ->
                val obj = data as? JsonObject
                if (obj == null) {
                    BadgerLog.w(TAG, "[$tag] config: expected object, got ${data::class.simpleName}")
                    return@unwrapApiResult ShortLinkConfig(false, false, false)
                }
                ShortLinkConfig.from(obj)
            }
    }

    
    fun listLinks(): List<ServerShortLink> {
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] shortlinks.list")
        return core.execute(core.request("GET", "/api/shortlinks/"))
            .unwrapApiResult("shortlinks.list", tag) { data ->
                val arr = (data as? JsonObject)?.get("links") as? JsonArray
                    ?: data as? JsonArray
                if (arr == null) {
                    BadgerLog.w(TAG, "[$tag] list: expected array or {links:[...]}, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    ServerShortLink.from(o)
                }
            }
    }

    

    fun createLink(originalURL: String, code: String? = null): String {
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            put("originalURL", originalURL)
            code?.takeIf { it.isNotBlank() }?.let { put("code", it) }
        }
        BadgerLog.d(TAG, "[$tag] shortlinks.create: url=${originalURL.take(80)} code=${code?.take(20)}")
        return core.execute(core.request("POST", "/api/shortlinks/", payload.toString()))
            .unwrapApiResult("shortlinks.create", tag) { data ->
                val uuid = if (data is JsonObject) {
                    stringOrNull(data, "uuid").orEmpty()
                } else ""
                if (uuid.isBlank()) throw ApiException(0, "shortlinks.create missing uuid", "shortlinks.create")
                uuid
            }
    }

    
    fun updateLink(uuid: String, originalURL: String? = null, code: String? = null) {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        val payload = buildJsonObject {
            originalURL?.let { put("originalURL", it) }
            code?.takeIf { it.isNotBlank() }?.let { put("code", it) }
        }
        BadgerLog.d(TAG, "[$tag] shortlinks.update: uuid=${uuid.take(8)}")
        core.execute(core.request("PUT", "/api/shortlinks/$uuid", payload.toString()))
            .unwrapApiResult("shortlinks.update", tag) {  }
    }

    
    fun deleteLink(uuid: String): Boolean {
        validateUuid(uuid)
        val tag = core.nextCallTag()
        BadgerLog.d(TAG, "[$tag] shortlinks.delete: uuid=${uuid.take(8)}")
        return try {
            core.execute(core.request("DELETE", "/api/shortlinks/$uuid"))
                .unwrapApiResult("shortlinks.delete", tag) { true }
        } catch (e: ApiException) {
            if (e.status == 404) {
                BadgerLog.d(TAG, "[$tag] delete 404 idempotent")
                true
            } else throw e
        }
    }

    private companion object {
        const val TAG = ApiCore.TAG

        fun validateUuid(uuid: String) {
            
            require(UUID_REGEX.matches(uuid)) {
                "invalid shortlink uuid: must be UUID format"
            }
        }

        private val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
