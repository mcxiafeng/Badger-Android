package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put

/** AI proxy endpoints — tag generation + contact OCR. */
class AiApi(private val core: ApiCore) {

    companion object {
        private const val TAG = "AiApi"
    }

    /** POST /api/proxy/ai/tasks/tag_generate {bio, existing_tags[]} */
    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> {
        val payload = buildJsonObject {
            put("bio", bio)
            put("existing_tags", JsonArray(existingTagNames.map { JsonPrimitive(it) }))
        }
        core.execute(core.buildRequest("POST", "/api/proxy/ai/tasks/tag_generate", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "tag_generate")
            // 2xx 空体走错误路径
            val bodyStr = resp.body?.string()
            if (bodyStr.isNullOrBlank()) {
                Log.w(TAG, "tag_generate: 2xx empty body")
                return emptyList()
            }
            val root = try { BadgerJson.parseToJsonElement(bodyStr) } catch (e: Exception) {
                Log.e(TAG, "tag_generate: malformed JSON", e)
                return emptyList()
            }
            val obj = root as? JsonObject
            if (obj == null) {
                Log.w(TAG, "tag_generate: expected object, got ${root::class.simpleName}")
                return emptyList()
            }
            val tags = obj["tags"] as? JsonArray ?: return emptyList()
            return tags.mapNotNull { el ->
                val o = el as? JsonObject ?: throw IllegalStateException("tag element not object")
                val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val conf = ((o["confidence"] as? JsonPrimitive)?.floatOrNull ?: 0.5f).coerceIn(0f, 1f)
                TagCandidate(name, conf)
            }
        }
    }

    /** POST /api/proxy/ai/tasks/contact_ocr. */
    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact {
        val payload = buildJsonObject {
            imageB64?.let { put("image_b64", it) }
            text?.let { put("text", it) }
        }
        core.execute(core.buildRequest("POST", "/api/proxy/ai/tasks/contact_ocr", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "contact_ocr")
            // 2xx 空体走错误路径
            val bodyStr = resp.body?.string()
            if (bodyStr.isNullOrBlank()) {
                throw ApiException(resp.code, "contact_ocr: 2xx empty body", "contact_ocr")
            }
            val root = try { BadgerJson.parseToJsonElement(bodyStr) } catch (e: Exception) {
                throw ApiException(resp.code, "contact_ocr: malformed JSON", "contact_ocr")
            }
            val obj = root as? JsonObject
                ?: throw ApiException(resp.code, "contact_ocr: expected object", "contact_ocr")
            return ExtractedContact.from(obj)
        }
    }
}

/** Resolver endpoints defined by the canonical Java `/api` contract. */
class ResolverApi(private val core: ApiCore) {

    /** POST /api/resolve/ — canonical single-item request. */
    fun resolveIdentify(input: String): JsonObject? {
        if (input.isBlank()) return null
        return try {
            val tag = core.nextCallTag()
            val payload = buildJsonObject { put("input", input) }
            core.execute(core.buildRequest("POST", "/api/resolve/", payload.toString()).build())
                .unwrapApiResult("identify", tag) { data ->
                    if (data !is JsonObject) {
                        Log.w(TAG, "[$tag] identify: expected ResolveResult object")
                        null
                    } else {
                        data
                    }
                }
        } catch (e: ApiException) {
            Log.w(TAG, "identify failed: code=${e.status} what=${e.what}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "identify failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * 批量解析：每个非空输入返回一个结果，失败条目为 null。
     */
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> {
        val clean = inputs.filter { it.isNotBlank() }
        if (clean.isEmpty()) return List(inputs.size) { null }

        return try {
            val tag = core.nextCallTag()
            val payload = buildJsonObject {
                put("items", JsonArray(clean.map { JsonPrimitive(it) }))
            }
            Log.d(TAG, "[$tag] identify batch: size=${clean.size}")
            core.execute(core.buildRequest("POST", "/api/resolve/", payload.toString()).build())
                .unwrapApiResult("identify.batch", tag) { data ->
                    val obj = data as? JsonObject
                    val results = obj?.get("results") as? JsonArray
                    if (results == null) {
                        Log.w(TAG, "[$tag] identify.batch: data.results missing or not an array")
                        return@unwrapApiResult List(clean.size) { null }
                    }
                    List(clean.size) { i ->
                        val e = if (i < results.size) results[i] else null
                        e as? JsonObject
                    }
                }
        } catch (e: ApiException) {
            Log.w(TAG, "identify batch size=${clean.size} failed: code=${e.status} what=${e.what}")
            List(clean.size) { null }
        } catch (e: Exception) {
            Log.w(TAG, "identify batch size=${clean.size} failed: ${e.javaClass.simpleName}: ${e.message}")
            List(clean.size) { null }
        }
    }

    /** GET /api/resolve/platforms — server-provided platform catalog. */
    fun platforms(): List<JsonObject> {
        val tag = core.nextCallTag()
        Log.d(TAG, "[$tag] platforms")
        return core.execute(core.buildRequest("GET", "/api/resolve/platforms").build())
            .unwrapApiResult("resolve.platforms", tag) { data ->
                val arr = data as? JsonArray
                if (arr == null) {
                    Log.w(TAG, "[$tag] platforms: expected array, got ${data::class.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                arr.mapNotNull { el -> el as? JsonObject }
            }
    }

    private companion object {
        const val TAG = ApiCore.TAG
    }
}

/** short.io proxy endpoints. */
class ShortLinkApi(private val core: ApiCore) {

    /** POST /api/proxy/shortio/links { action: "list" } */
    fun shortioList(): JsonObject {
        val payload = buildJsonObject {
            put("action", "list")
            put("limit", 50)
        }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.list")
            // 2xx 空体防护
            val bodyStr = resp.body?.string()
                ?: throw ApiException(resp.code, "shortio.list: empty body", "shortio.list")
            return BadgerJson.parseToJsonElement(bodyStr) as JsonObject
        }
    }

    /** POST /api/proxy/shortio/links/{id} { originalURL } */
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject {
        val payload = buildJsonObject { put("originalURL", newUrl) }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links/$linkId", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.update")
            val bodyStr = resp.body?.string()
                ?: throw ApiException(resp.code, "shortio.update: empty body", "shortio.update")
            return BadgerJson.parseToJsonElement(bodyStr) as JsonObject
        }
    }

    /** POST /api/proxy/shortio/domains */
    fun shortioDomains(): JsonObject {
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/domains", "{}").build()).use { resp ->
            core.ensureOk(resp, "shortio.domains")
            val bodyStr = resp.body?.string()
                ?: throw ApiException(resp.code, "shortio.domains: empty body", "shortio.domains")
            return BadgerJson.parseToJsonElement(bodyStr) as JsonObject
        }
    }

    /** POST /api/proxy/shortio/links { action: "create", originalURL, domainId? } */
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject {
        val payload = buildJsonObject {
            put("action", "create")
            put("originalURL", originalUrl)
            domainId?.takeIf { it > 0 }?.let { put("domainId", it) }
        }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.create")
            val bodyStr = resp.body?.string()
                ?: throw ApiException(resp.code, "shortio.create: empty body", "shortio.create")
            return BadgerJson.parseToJsonElement(bodyStr) as JsonObject
        }
    }
}
