package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** AI proxy endpoints — tag generation + contact OCR. */
class AiApi(private val core: ApiCore) {

    /** POST /api/proxy/ai/tasks/tag_generate {bio, existing_tags[]} */
    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> {
        val payload = JsonObject().apply {
            addProperty("bio", bio)
            val arr = JsonArray()
            existingTagNames.forEach { arr.add(it) }
            add("existing_tags", arr)
        }
        core.execute(core.buildRequest("POST", "/api/proxy/ai/tasks/tag_generate", payload.toString()).build()).use { resp ->
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

    /** POST /api/proxy/ai/tasks/contact_ocr. */
    fun contactOcr(imageB64: String? = null, text: String? = null): ExtractedContact {
        val payload = JsonObject().apply {
            imageB64?.let { addProperty("image_b64", it) }
            text?.let { addProperty("text", it) }
        }
        core.execute(core.buildRequest("POST", "/api/proxy/ai/tasks/contact_ocr", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "contact_ocr")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
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
            val payload = JsonObject().apply { addProperty("input", input) }
            core.execute(core.buildRequest("POST", "/api/resolve/", payload.toString()).build())
                .unwrapApiResult("identify", tag) { data ->
                    if (!data.isJsonObject) {
                        Log.w(TAG, "[$tag] identify: expected ResolveResult object")
                        null
                    } else {
                        data.asJsonObject
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
     * POST /api/resolve/ (trailing slash).
     *
     * Returns one JSON object per non-blank input, in input order; malformed
     * or failed items are represented by null. Callers preserve original
     * positions when filtering blank inputs.
     */
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> {
        val clean = inputs.filter { it.isNotBlank() }
        if (clean.isEmpty()) return List(inputs.size) { null }

        return try {
            val tag = core.nextCallTag()
            val payload = JsonObject().apply {
                val arr = JsonArray()
                clean.forEach { arr.add(it) }
                add("items", arr)
            }
            Log.d(TAG, "[$tag] identify batch: size=${clean.size}")
            core.execute(core.buildRequest("POST", "/api/resolve/", payload.toString()).build())
                .unwrapApiResult("identify.batch", tag) { data ->
                    val results = data.asJsonObject
                        .get("results")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                    if (results == null) {
                        Log.w(TAG, "[$tag] identify.batch: data.results missing or not an array")
                        return@unwrapApiResult List(clean.size) { null }
                    }
                    List(clean.size) { i ->
                        val e = if (i < results.size()) results.get(i) else null
                        if (e != null && e.isJsonObject) e.asJsonObject else null
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
                if (!data.isJsonArray) {
                    Log.w(TAG, "[$tag] platforms: expected array, got ${data.javaClass.simpleName}")
                    return@unwrapApiResult emptyList()
                }
                data.asJsonArray.mapNotNull { el -> if (el.isJsonObject) el.asJsonObject else null }
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
        val payload = JsonObject().apply { addProperty("action", "list"); addProperty("limit", 50) }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.list")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /api/proxy/shortio/links/{id} { originalURL } */
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject {
        val payload = JsonObject().apply { addProperty("originalURL", newUrl) }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links/$linkId", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.update")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /api/proxy/shortio/domains */
    fun shortioDomains(): JsonObject {
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/domains", "{}").build()).use { resp ->
            core.ensureOk(resp, "shortio.domains")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /api/proxy/shortio/links { action: "create", originalURL, domainId? } */
    fun shortioCreate(originalUrl: String, domainId: Long? = null): JsonObject {
        val payload = JsonObject().apply {
            addProperty("action", "create")
            addProperty("originalURL", originalUrl)
            domainId?.takeIf { it > 0 }?.let { addProperty("domainId", it) }
        }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.create")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }
}
