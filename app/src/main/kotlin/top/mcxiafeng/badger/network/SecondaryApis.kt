package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * [§15 #19] AI proxy endpoints — tag generation + contact OCR.
 */
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

    /**
     * POST /api/proxy/ai/tasks/contact_ocr
     *
     * Pass [imageB64] for vision mode, [text] for text mode.
     */
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

/**
 * [Phase 4] Resolver endpoint — single authoritative identification entry.
 *
 * 新 Java `/api` 契约（`Badger-Server/docs/api-handover.md` §5）：
 * - 路径 `POST /api/resolve/`（**必须带尾斜杠**，否则 404）；
 * - 批量 body `{ items: ["<input>", ...] }`（上限 50，单条非法只污染该条）；
 * - 响应一律 ApiResult 壳：批量 `data = { results: [...] }`，按输入顺序返回；
 * - ResolveResult 字段 camelCase（`avatarUrl`/`description`/`contacts`/`jumpLink`…）。
 */
class ResolverApi(private val core: ApiCore) {

    /** POST /api/resolve/ — 单条识别（批量 of 1，复用同一解析路径）。 */
    fun resolveIdentify(input: String): JsonObject? = resolveIdentifyBatch(listOf(input)).firstOrNull()

    /**
     * POST /api/resolve/（尾斜杠）— 批量识别。
     *
     * Returns one [JsonObject] per input URL in order (null on failure).
     * Empty / blank inputs are filtered out up front; positions are preserved
     * by the caller ([ContactNetworkResolver.identifyBatch] zips back to the
     * original index).
     *
     * 之所以不强制每条一次调用:多码模式下用户一次性扫到 N 个码,旧实现对每个 URL 单独 POST,
     * 服务端处理 N 次 + TLS 握手 N 次 + dispatcher 排队 N 次 — 用户拿到的"一次性"的
     * 网络结果其实是 N 次串行握手。批量提交在客户端用一次 RTT 换取 N 个结果。
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
                    // [修复防御]: 批量响应契约是 data = { results: [...] }；防御性兼容
                    // 某些版本直接返回数组。results 缺失/非数组 → 整批按 null 处理
                    // （有日志，不吞根因）。
                    val results = when {
                        data.isJsonObject ->
                            data.asJsonObject.get("results")?.takeIf { it.isJsonArray }?.asJsonArray
                        data.isJsonArray -> data.asJsonArray
                        else -> null
                    }
                    if (results == null) {
                        Log.w(TAG, "[$tag] identify.batch: missing data.results, got ${data.javaClass.simpleName}")
                        return@unwrapApiResult List(clean.size) { null }
                    }
                    // 服务端按输入顺序返回,与 clean 同长;不足的位补 null(逐条失败不连坐整批)。
                    List(clean.size) { i ->
                        val e = if (i < results.size()) results.get(i) else null
                        if (e != null && e.isJsonObject) e.asJsonObject else null
                    }
                }
        } catch (e: ApiException) {
            Log.w(TAG, "identify batch size=${clean.size} failed: code=${e.status} what=${e.what}")
            List(clean.size) { null }
        } catch (e: Exception) {
            // [修复防御]: 旧契约残留的 HTML 兜底 / class cast — 之前因为路径错发到 SPA fallback,
            // 返 HTML 整段失败 → 整个 resolver 链断开 → 所有联系人都是"未知联系人"。
            // 现在新契约即便失败也不会让模块级崩,只整批记录返回 null。
            Log.w(TAG, "identify batch size=${clean.size} parse failed: ${e.javaClass.simpleName}: ${e.message}")
            List(clean.size) { null }
        }
    }

    /** GET /api/resolve/platforms — 服务端可解析平台清单（含自定义，过滤禁用）。 */
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

/**
 * [§15 #19] short.io link management endpoints.
 */
class ShortLinkApi(private val core: ApiCore) {

    /** POST /api/proxy/shortio/links  { action: "list" } */
    fun shortioList(): JsonObject {
        val payload = JsonObject().apply { addProperty("action", "list"); addProperty("limit", 50) }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.list")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /api/proxy/shortio/links/{id}  {originalURL} */
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject {
        val payload = JsonObject().apply { addProperty("originalURL", newUrl) }
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/links/$linkId", payload.toString()).build()).use { resp ->
            core.ensureOk(resp, "shortio.update")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /**
     * POST /api/proxy/shortio/domains
     */
    fun shortioDomains(): JsonObject {
        core.execute(core.buildRequest("POST", "/api/proxy/shortio/domains", "{}").build()).use { resp ->
            core.ensureOk(resp, "shortio.domains")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /**
     * POST /api/proxy/shortio/links  {action: "create", originalURL, domainId?}
     */
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
