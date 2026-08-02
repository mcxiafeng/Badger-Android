package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * [§15 #19] AI proxy endpoints — tag generation + contact OCR.
 */
class AiApi(private val core: ApiCore) {

    /** POST /v1/proxy/ai/tasks/tag_generate {bio, existing_tags[]} */
    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> {
        val payload = JsonObject().apply {
            addProperty("bio", bio)
            val arr = JsonArray()
            existingTagNames.forEach { arr.add(it) }
            add("existing_tags", arr)
        }
        core.execute(core.buildRequest("POST", "/v1/proxy/ai/tasks/tag_generate", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "tag_generate")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            val tags = obj.getAsJsonArray("tags") ?: return emptyList()
            return tags.mapNotNull { el ->
                val o = el.asJsonObject
                val name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val conf = (o.get("confidence")?.asFloat ?: 0.5f).coerceIn(0f, 1f)
                TagCandidate(name, conf)
            }
        }
    }

    /**
     * POST /v1/proxy/ai/tasks/contact_ocr
     *
     * Pass [imageB64] for vision mode, [text] for text mode.
     */
    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact {
        val payload = JsonObject().apply {
            imageB64?.let { addProperty("image_b64", it) }
            text?.let { addProperty("text", it) }
        }
        core.execute(core.buildRequest("POST", "/v1/proxy/ai/tasks/contact_ocr", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "contact_ocr")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return ExtractedContact.from(obj)
        }
    }
}

/**
 * [§15 #19] Resolver endpoint — single authoritative identification entry.
 */
class ResolverApi(private val core: ApiCore) {

    /**
     * POST /v1/resolver/identify {input}
     *
     * Returns: `{kind, name, avatar_url, signature, contact_map}` where
     * `kind` is one of:
     *   "github" | "bilibili" | "qq" | "x" | "telegram"
     *   | "qqGroup" | "telegramGroup"
     *   | "wechat" | "douyin" | "weibo" | "xiaohongshu" | "facebook"
     *   | "website" | "unknown"
     */
    fun resolveIdentify(input: String): JsonObject? {
        if (input.isBlank()) {
            return null
        }
        return try {
            val payload = JsonObject().apply { addProperty("input", input) }
            core.execute(core.buildRequest("POST", "/v1/resolver/identify", payload.toString()).build()).use { resp ->
                core.ensureOk(resp, "identify")
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
        } catch (e: ApiException) {
            Log.w(ApiCore.TAG, "identify[$input] failed: code=${e.status} what=${e.what}")
            null
        }
    }
}

/**
 * [§15 #19] short.io link management endpoints.
 */
class ShortLinkApi(private val core: ApiCore) {

    /** POST /v1/proxy/shortio/links  { action: "list" } */
    fun shortioList(): JsonObject {
        val payload = JsonObject().apply { addProperty("action", "list"); addProperty("limit", 50) }
        core.execute(core.buildRequest("POST", "/v1/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.list")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /v1/proxy/shortio/links/{id}  {originalURL} */
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject {
        val payload = JsonObject().apply { addProperty("originalURL", newUrl) }
        core.execute(core.buildRequest("POST", "/v1/proxy/shortio/links/$linkId", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.update")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /**
     * POST /v1/proxy/shortio/domains
     */
    fun shortioDomains(): JsonObject {
        core.execute(core.buildRequest("POST", "/v1/proxy/shortio/domains", "{}").build()).use { resp ->
            core.ensureOk(resp, "shortio.domains")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /**
     * POST /v1/proxy/shortio/links  {action: "create", originalURL, domainId?}
     */
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject {
        val payload = JsonObject().apply {
            addProperty("action", "create")
            addProperty("originalURL", originalUrl)
            domainId?.takeIf { it > 0 }?.let { addProperty("domainId", it) }
        }
        core.execute(core.buildRequest("POST", "/v1/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.create")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }
}
