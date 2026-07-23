package top.mcxiafeng.badger.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.mcxiafeng.badger.utils.SafeLog
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ServerApi"

/**
 * Thin REST client for the Badger-Server HTTP surface. Replaces the
 * mixture of inline WebClient/WebDavClient/AiOcrService/PlatformAdapter
 * calls that used to live in the app — every AI, resolver, backup, and
 * short-link call now goes through here.
 *
 * Construction: [ServerClient] holds the [OkHttpClient] + JWT; this object
 * is the request DSL and JSON-binding façade.
 *
 * Hot-reload: [baseUrl] is `@Volatile var`, mutated only via
 * [setBaseUrl] (called by [top.mcxiafeng.badger.data.repository.ServerApiFactory]).
 * Each request reads the current value, so a URL change applies without
 * rebuilding the shared instance.
 */
class ServerApi(
    @Volatile private var baseUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
) {
    /**
     * Sequential call id used to correlate auth flow logs (login → me → refresh)
     * inside logcat. Resetting to 0 would be a sign of misuse; tests can read
     * this through [nextCallTag] if they need to assert ordering.
     */
    private val callSeq = AtomicLong(0)

    private fun nextCallTag(): String {
        val seq = callSeq.incrementAndGet()
        val base = baseUrl.trimEnd('/')
        // Strip scheme + host only — never log path or query, since auth
        // requests carry tokens in some upstream schemes and we want to be
        // conservative here.
        val host = base.substringAfter("://", missingDelimiterValue = base)
        return "auth#$seq@$host"
    }

    /**
     * Update the base URL used for every subsequent request. Must only be
     * called from [ServerApiFactory]; call sites should go through
     * `ServerApiFactory.updateBaseUrl()` which also persists to prefs.
     *
     * No-op when [newUrl] equals the current value.
     */
    fun setBaseUrl(newUrl: String) {
        if (newUrl == baseUrl) return
        Log.d(TAG, "setBaseUrl: $baseUrl -> $newUrl")
        baseUrl = newUrl
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun urlOf(path: String): String =
        if (baseUrl.endsWith("/") || path.startsWith("/")) "$baseUrl$path"
        else "$baseUrl/$path"

    private fun buildRequest(
        method: String,
        path: String,
        body: String? = null,
    ): Request.Builder {
        val b = Request.Builder().url(urlOf(path))
        tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        when (method) {
            "GET" -> b.get()
            "DELETE" -> b.delete()
            "POST" -> b.post((body ?: "{}").toRequestBody(jsonMedia))
            "PATCH" -> b.patch((body ?: "{}").toRequestBody(jsonMedia))
            "PUT" -> b.put((body ?: "{}").toRequestBody(jsonMedia))
            else -> error("unsupported method $method")
        }
        return b
    }

    @Throws(IOException::class)
    private fun execute(req: Request): Response = http.newCall(req).execute()

    private fun ensureOk(resp: Response, what: String) {
        if (!resp.isSuccessful) {
            val err = resp.body?.string()?.ifBlank { null } ?: resp.message
            resp.close()
            throw ApiException(resp.code, err, what)
        }
    }

    // -------- auth --------

    data class AuthResponse(val token: String, val expiresIn: Int, val role: String?, val username: String?)

    /** POST /api/auth/register {username, password, email?, display_name?} */
    fun register(username: String, password: String, email: String?, displayName: String?): AuthResponse {
        val tag = nextCallTag()
        Log.d(TAG, "[$tag] register: user=${SafeLog.user(username)} email=${SafeLog.email(email)}")
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
            email?.takeIf { it.isNotBlank() }?.let { addProperty("email", it) }
            displayName?.takeIf { it.isNotBlank() }?.let { addProperty("display_name", it) }
        }
        return try {
            execute(buildRequest("POST", "/api/auth/register", payload.toString()).build()).use { resp ->
                ensureOk(resp, "register")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                val usernameEcho = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] register OK: code=${resp.code} user=${SafeLog.user(usernameEcho)} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                    username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] register failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/login {username, password} */
    fun login(username: String, password: String): AuthResponse {
        val tag = nextCallTag()
        Log.d(TAG, "[$tag] login: user=${SafeLog.user(username)} passwordLen=${password.length}")
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }
        return try {
            execute(buildRequest("POST", "/api/auth/login", payload.toString()).build()).use { resp ->
                ensureOk(resp, "login")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                val roleEcho = obj.get("role")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] login OK: code=${resp.code} role=${roleEcho ?: "<none>"} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                    username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        } catch (e: java.net.ConnectException) {
            // [修复防御]: OkHttp 把"连不上服务端"包成 ConnectException,message 通常是
            // "Failed to connect to /192.168.x.x:port",reason 字段会显示具体原因
            // (Connection refused / Network is unreachable 等)。这里把完整诊断打出来,
            // 便于排查 "服务器没看到任何连接" —— 可能是 APP 没真的发出去。
            Log.w(TAG, "[$tag] login ConnectException: msg=${e.message} reason=${(e.cause as? java.net.SocketException)?.message ?: e.cause?.javaClass?.simpleName}", e)
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "[$tag] login SocketTimeoutException: msg=${e.message}", e)
            throw e
        } catch (e: java.net.UnknownHostException) {
            // 关键诊断:这种错常发生在"Private DNS"或 VPN 拦截纯 IP / DNS 解析失败时
            Log.w(TAG, "[$tag] login UnknownHostException: msg=${e.message}", e)
            throw e
        } catch (e: java.io.IOException) {
            // [修复防御]: 其他 IO 异常统一抓,打出完整类名 + message + cause 链,
            // 避免像之前一样 "status=<n/a> msg=null" 这种什么都看不到的情况
            var cur: Throwable? = e
            var depth = 0
            val chain = buildString {
                while (cur != null && depth < 5) {
                    append(" -> [${cur.javaClass.name}] ${cur.message}")
                    cur = cur.cause
                    depth++
                }
            }
            Log.w(TAG, "[$tag] login IOException chain:$chain", e)
            throw e
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] login failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/refresh — server requires the current token. */
    fun refresh(): AuthResponse {
        val tag = nextCallTag()
        Log.d(TAG, "[$tag] refresh: issuing with current token")
        return try {
            execute(buildRequest("POST", "/api/auth/refresh").build()).use { resp ->
                ensureOk(resp, "refresh")
                val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
                val tokenLen = obj.get("token").asString.length
                Log.d(TAG, "[$tag] refresh OK: code=${resp.code} tokenLen=$tokenLen")
                AuthResponse(
                    token = obj.get("token").asString,
                    expiresIn = obj.get("expires_in")?.asInt ?: 0,
                    role = null,
                    username = null,
                )
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] refresh failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** POST /api/auth/logout */
    fun logout() {
        val tag = nextCallTag()
        Log.d(TAG, "[$tag] logout: server-side revoke")
        try {
            execute(buildRequest("POST", "/api/auth/logout").build()).use { resp ->
                // 204 = ok; anything 2xx is fine. We tolerate 401 (token gone).
                if (resp.code !in 200..299 && resp.code != 401) {
                    Log.w(TAG, "[$tag] logout non-2xx: code=${resp.code}")
                    throw ApiException(resp.code, resp.message, "logout")
                }
                Log.d(TAG, "[$tag] logout OK: code=${resp.code}")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] logout failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    /** GET /api/auth/me */
    fun me(): JsonObject {
        val tag = nextCallTag()
        Log.d(TAG, "[$tag] me: fetching profile")
        return try {
            execute(buildRequest("GET", "/api/auth/me").build()).use { resp ->
                ensureOk(resp, "me")
                val body = resp.body!!.string()
                val obj = JsonParser.parseString(body).asJsonObject
                // 仅记录非敏感的稳定字段,绝不打印 email / phone 等隐私值
                val username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString
                val role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString
                Log.d(TAG, "[$tag] me OK: code=${resp.code} user=${SafeLog.user(username)} role=${role ?: "<none>"}")
                obj
            }
        } catch (e: ApiException) {
            Log.w(TAG, "[$tag] me failed: code=${e.status} what=${e.what}")
            throw e
        }
    }

    // -------- AI: tag + OCR --------

    /** POST /v1/proxy/ai/tasks/tag_generate {bio, existing_tags[]} */
    fun tagGenerate(bio: String, existingTagNames: List<String>): List<TagCandidate> {
        val payload = JsonObject().apply {
            addProperty("bio", bio)
            val arr = JsonArray()
            existingTagNames.forEach { arr.add(it) }
            add("existing_tags", arr)
        }
        execute(buildRequest("POST", "/v1/proxy/ai/tasks/tag_generate", payload.toString()).build()).use { resp ->
            ensureOk(resp, "tag_generate")
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
        execute(buildRequest("POST", "/v1/proxy/ai/tasks/contact_ocr", payload.toString()).build()).use { resp ->
            ensureOk(resp, "contact_ocr")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return ExtractedContact.from(obj)
        }
    }

    // -------- resolver --------

    /**
     * Single authoritative identification entry point on the server.
     *
     * POST /v1/resolver/identify {input}
     *
     * Returns: `{kind, name, avatar_url, signature, contact_map}` where
     * `kind` is one of:
     *   "github" | "bilibili" | "qq" | "x" | "telegram"
     *   | "qqGroup" | "telegramGroup"
     *   | "wechat" | "douyin" | "weibo" | "xiaohongshu" | "facebook"
     *   | "website" | "unknown"
     *
     * The client previously did its own URL host parsing + per-platform
     * extract via 5 private regexes in [ContactNetworkResolver] —
     * those are now removed. All recognition (URL vs. raw text vs.
     * numeric QQ vs. group invite) belongs to the server.
     */
    fun resolveIdentify(input: String): JsonObject? {
        if (input.isBlank()) {
            // Server can't classify an empty payload; short-circuit so we
            // don't burn a request on a known-uninteresting input.
            return null
        }
        return try {
            val payload = JsonObject().apply { addProperty("input", input) }
            execute(buildRequest("POST", "/v1/resolver/identify", payload.toString()).build()).use { resp ->
                ensureOk(resp, "identify")
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
        } catch (e: ApiException) {
            // [修复防御]: 与 getObject 同模式 — 401 已被 NetworkModule 拦截器处理过,
            // 这里命中意味着 token 真失效 / 路由缺失 / 客户端 payload schema 不匹配,
            // 全部冒上来,不再静默吞。
            Log.w(TAG, "identify[$input] failed: code=${e.status} what=${e.what}")
            null
        }
    }

    /** GET /v1/resolver/github/{login} */
    fun resolveGitHub(login: String): JsonObject? =
        getObject("/v1/resolver/github/$login")

    /** GET /v1/resolver/bili/{uid} */
    fun resolveBili(uid: String): JsonObject? =
        getObject("/v1/resolver/bili/$uid")

    /** GET /v1/resolver/qq/{qq} */
    fun resolveQq(qq: String): JsonObject? =
        getObject("/v1/resolver/qq/$qq")

    /** GET /v1/resolver/twitter/{handle} */
    fun resolveTwitter(handle: String): JsonObject? =
        getObject("/v1/resolver/twitter/$handle")

    /** GET /v1/resolver/telegram/{path} */
    fun resolveTelegram(path: String): JsonObject? =
        getObject("/v1/resolver/telegram/$path")

    /** GET /v1/resolver/qq-avatar/{qq} */
    fun resolveQqAvatar(qq: String): JsonObject? =
        getObject("/v1/resolver/qq-avatar/$qq")

    /** GET /v1/resolver/favicon?url=... */
    fun resolveFavicon(url: String): JsonObject? {
        val full = urlOf("/v1/resolver/favicon?url=" + java.net.URLEncoder.encode(url, "UTF-8"))
        val reqBuilder = Request.Builder().url(full)
        tokenProvider()?.let { reqBuilder.header("Authorization", "Bearer $it") }
        reqBuilder.get()
        return try {
            execute(reqBuilder.build()).use { resp ->
                ensureOk(resp, "favicon")
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
        } catch (_: ApiException) {
            null
        }
    }

    private fun getObject(path: String): JsonObject? {
        return try {
            execute(buildRequest("GET", path).build()).use { resp ->
                ensureOk(resp, path)
                JsonParser.parseString(resp.body!!.string()).asJsonObject
            }
        } catch (e: ApiException) {
            // [修复防御]: 不再静默吞掉 —— 把 status + path 打出来供排查。
            // 历史问题:`_ : ApiException` 一律吃掉,扫码时 `/v1/resolver/*` 被服务端
            // 401 拒绝后 UI 拿到的 null,根因完全埋在静默回退里。
            // 注意:401 通常已被 NetworkModule.tokenRefreshInterceptor 拦截并重试过一次
            // (refresh + 用新 token 再发),仍 401 才是真正的 token 失效,这里只是兜底。
            Log.w(TAG, "getObject[$path] failed: code=${e.status} what=${e.what}")
            null
        }
    }

    // -------- short links --------

    /** POST /v1/proxy/shortio/links  { action: "list" } */
    fun shortioList(): JsonObject {
        val payload = JsonObject().apply { addProperty("action", "list"); addProperty("limit", 50) }
        execute(buildRequest("POST", "/v1/proxy/shortio/links", payload.toString()).build()).use { resp ->
            ensureOk(resp, "shortio.list")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    /** POST /v1/proxy/shortio/links/{id}  {originalURL} */
    fun shortioUpdate(linkId: String, newUrl: String): JsonObject {
        val payload = JsonObject().apply { addProperty("originalURL", newUrl) }
        execute(buildRequest("POST", "/v1/proxy/shortio/links/$linkId", payload.toString()).build()).use { resp ->
            ensureOk(resp, "shortio.update")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
        }
    }

    // -------- backups --------

    data class BackupSummary(val id: String, val name: String, val size: Long, val createdAt: String)

    /** GET /v1/backups */
    fun listBackups(): List<BackupSummary> {
        execute(buildRequest("GET", "/v1/backups").build()).use { resp ->
            ensureOk(resp, "backups.list")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            val arr = obj.getAsJsonArray("backups") ?: return emptyList()
            return arr.mapNotNull {
                val o = it.asJsonObject
                BackupSummary(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    size = o.get("size").asLong,
                    createdAt = o.get("created_at").asString,
                )
            }
        }
    }

    /** POST /v1/backups with a JSON envelope. */
    data class BackupUpload(val id: String, val name: String, val size: Long, val createdAt: String)

    fun uploadBackup(envelopeJson: String): BackupUpload {
        execute(buildRequest("POST", "/v1/backups", envelopeJson).build()).use { resp ->
            ensureOk(resp, "backups.upload")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return BackupUpload(
                id = obj.get("id").asString,
                name = obj.get("name").asString,
                size = obj.get("size").asLong,
                createdAt = obj.get("created_at").asString,
            )
        }
    }

    /** GET /v1/backups/{id} returns raw bytes; use [OkHttp] directly from the caller when needed. */
    fun downloadBackup(id: String): ByteArray {
        execute(buildRequest("GET", "/v1/backups/$id").build()).use { resp ->
            ensureOk(resp, "backups.download")
            return resp.body!!.bytes()
        }
    }
}

/** Thrown when the server returns a non-2xx response. */
class ApiException(val status: Int, val bodyText: String?, val what: String) :
    IOException("$what failed: HTTP $status  ${bodyText ?: ""}")

/** Single tag candidate from the server `/v1/proxy/ai/tasks/tag_generate`. */
data class TagCandidate(val name: String, val confidence: Float)

/**
 * Mirror of the server's contact-OCR schema. All contact fields are nullable
 * because the upstream LLM may legitimately return null. [other] is a list
 * of leftover strings that didn't fit any typed column.
 */
data class ExtractedContact(
    val name: String?,
    val phone: String?,
    val email: String?,
    val avatarUrl: String? = null,
    val qq: String?,
    val wechat: String?,
    val bilibili: String?,
    val weibo: String?,
    val douyin: String?,
    val github: String?,
    val telegram: String?,
    val xiaohongshu: String?,
    val facebook: String?,
    val x: String?,
    val website: String?,
    val other: List<String>,
) {
    companion object {
        fun from(o: JsonObject): ExtractedContact = ExtractedContact(
            name = stringOrNull(o, "name"),
            phone = stringOrNull(o, "phone"),
            email = stringOrNull(o, "email"),
            avatarUrl = stringOrNull(o, "avatar_url"),
            qq = stringOrNull(o, "qq"),
            wechat = stringOrNull(o, "wechat"),
            bilibili = stringOrNull(o, "bilibili"),
            weibo = stringOrNull(o, "weibo"),
            douyin = stringOrNull(o, "douyin"),
            github = stringOrNull(o, "github"),
            telegram = stringOrNull(o, "telegram"),
            xiaohongshu = stringOrNull(o, "xiaohongshu"),
            facebook = stringOrNull(o, "facebook"),
            x = stringOrNull(o, "x"),
            website = stringOrNull(o, "website"),
            other = o.getAsJsonArray("other")?.mapNotNull { it.takeIfString() } ?: emptyList(),
        )
    }
}

private fun stringOrNull(o: JsonObject, key: String): String? {
    val v = o.get(key) ?: return null
    if (v.isJsonNull) return null
    return v.takeIfString()
}

private fun com.google.gson.JsonElement.takeIfString(): String? =
    if (this.isJsonNull) null else this.asString?.takeIf { it.isNotBlank() }
