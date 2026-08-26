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
     * POST /v1/resolver {urls: ["<input>"]}
     *
     * Returns: array of `{platform, url, status, name, avatar_url, signature, id, jump_link, contact_map}`
     * per input. `platform` is one of:
     *   "github" | "bilibili" | "qq" | "x" | "telegram"
     *   | "qqGroup" | "telegramGroup"
     *   | "wechat" | "douyin" | "weibo" | "xiaohongshu" | "facebook"
     *   | "website" | "unknown"
     *
     * [修复]: 原来客户端打 `/v1/resolver/identify` body=`{"input":...}`,
     * 旧版服务端契约。新版服务端 `/v1/resolver` 需要 body=`{"urls":[...]}` 数组。
     * 改路径 + payload 自动重试一次 receive — 接收方仍是单个输入,但服务端要 [1] 长数组。
     */
    fun resolveIdentify(input: String): JsonObject? = resolveIdentifyBatch(listOf(input)).firstOrNull()

    /**
     * Batch variant: POST once with all URLs in the same array. Server returns an array
     * in input order — caller is responsible for zipping it back. Empty / blank inputs
     * are filtered out up front (server would reject / mis-classify them).
     *
     * 之所以不强制每条一次调用:多码模式下用户一次性扫到 N 个码,旧实现对每个 URL 单独 POST,
     * 服务端处理 N 次 + TLS 握手 N 次 + dispatcher 排队 N 次 — 用户拿到的"一次性"的
     * 网络结果其实是 N 次串行握手。批量提交在客户端用一次 RTT 换取 N 个结果。
     */
    fun resolveIdentifyBatch(inputs: List<String>): List<JsonObject?> {
        val clean = inputs.filter { it.isNotBlank() }
        if (clean.isEmpty()) return List(inputs.size) { null }
        return try {
            val payload = JsonObject().apply {
                val arr = JsonArray()
                clean.forEach { arr.add(it) }
                add("urls", arr)
            }
            core.execute(core.buildRequest("POST", "/v1/resolver", payload.toString()).build()).use { resp ->
                core.ensureOk(resp, "identify")
                val json = JsonParser.parseString(resp.body!!.string())
                // 服务端必须按输入顺序返回 N 条,与 inputs 同长。空位补 null。
                val raw: List<JsonObject?> = when {
                    json.isJsonArray -> {
                        val arr = json.asJsonArray
                        List(clean.size) { i ->
                            val e = arr.get(i)
                            if (e != null && e.isJsonObject) e.asJsonObject else null
                        }
                    }
                    json.isJsonObject -> List(clean.size) { json.asJsonObject }
                    else -> List(clean.size) { null }
                }
                Log.d(ApiCore.TAG, "identify: batch=${clean.size} got=${raw.count { it != null }}")
                raw
            }
        } catch (e: ApiException) {
            Log.w(ApiCore.TAG, "identify batch size=${clean.size} failed: code=${e.status} what=${e.what}")
            List(clean.size) { null }
        } catch (e: Exception) {
            // [修复防御]: 旧契约残留的 HTML 兜底 / class cast — 之前因为路径错发到 SPA fallback,
            // 返 HTML 整段失败 → 整个 resolver 链断开 → 所有联系人都是"未知联系人"。
            // 现在新契约即便失败也不会让模块级崩,只整批记录返回 null。
            Log.w(ApiCore.TAG, "identify batch size=${clean.size} parse failed: ${e.javaClass.simpleName}: ${e.message}")
            List(clean.size) { null }
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
