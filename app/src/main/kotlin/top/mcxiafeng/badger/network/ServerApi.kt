package top.mcxiafeng.badger.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Thin REST client for the Badger-Server HTTP surface. Replaces the
 * mixture of inline WebClient/WebDavClient/AiOcrService/PlatformAdapter
 * calls that used to live in the app — every AI, resolver, backup, and
 * short-link call now goes through here.
 *
 * Construction: [ServerClient] holds the [OkHttpClient] + JWT; this object
 * is the request DSL and JSON-binding façade.
 */
class ServerApi(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
) {
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
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
            email?.takeIf { it.isNotBlank() }?.let { addProperty("email", it) }
            displayName?.takeIf { it.isNotBlank() }?.let { addProperty("display_name", it) }
        }
        execute(buildRequest("POST", "/api/auth/register", payload.toString()).build()).use { resp ->
            ensureOk(resp, "register")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return AuthResponse(
                token = obj.get("token").asString,
                expiresIn = obj.get("expires_in")?.asInt ?: 0,
                role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
    }

    /** POST /api/auth/login {username, password} */
    fun login(username: String, password: String): AuthResponse {
        val payload = JsonObject().apply {
            addProperty("username", username)
            addProperty("password", password)
        }
        execute(buildRequest("POST", "/api/auth/login", payload.toString()).build()).use { resp ->
            ensureOk(resp, "login")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return AuthResponse(
                token = obj.get("token").asString,
                expiresIn = obj.get("expires_in")?.asInt ?: 0,
                role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString,
                username = obj.get("username")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
    }

    /** POST /api/auth/refresh — server requires the current token. */
    fun refresh(): AuthResponse {
        execute(buildRequest("POST", "/api/auth/refresh").build()).use { resp ->
            ensureOk(resp, "refresh")
            val obj = JsonParser.parseString(resp.body!!.string()).asJsonObject
            return AuthResponse(
                token = obj.get("token").asString,
                expiresIn = obj.get("expires_in")?.asInt ?: 0,
                role = null,
                username = null,
            )
        }
    }

    /** POST /api/auth/logout */
    fun logout() {
        execute(buildRequest("POST", "/api/auth/logout").build()).use { resp ->
            // 204 = ok; anything 2xx is fine. We tolerate 401 (token gone).
            if (resp.code !in 200..299 && resp.code != 401) {
                throw ApiException(resp.code, resp.message, "logout")
            }
        }
    }

    /** GET /api/auth/me */
    fun me(): JsonObject {
        execute(buildRequest("GET", "/api/auth/me").build()).use { resp ->
            ensureOk(resp, "me")
            return JsonParser.parseString(resp.body!!.string()).asJsonObject
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
        } catch (_: ApiException) {
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
