package top.mcxiafeng.badger.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * [§15 #19] Shared HTTP plumbing for the per-domain API classes extracted from
 * the old monolithic [ServerApi].
 *
 * Each domain class ([ContactApi], [AuthApi], [AiApi], [ResolverApi],
 * [ShortLinkApi], [BackupApi]) holds an [ApiCore] and uses it to build
 * requests, assign call tags, and normalize error / conflict responses.
 *
 * Why a core class instead of `object ApiCore`:
 * - `baseUrl` is `@Volatile var` mutated at runtime via
 *   [ServerApi.setBaseUrl]; it must be per-instance, not static.
 * - Tests can construct an [ApiCore] with a stub [OkHttpClient] / baseUrl.
 */
class ApiCore(
    @Volatile var baseUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
) {
    private val callSeq = AtomicLong(0)

    /**
     * Sequential call id used to correlate flow logs (login → me → refresh) in
     * logcat. Resetting to 0 would be a sign of misuse.
     */
    fun nextCallTag(): String {
        val seq = callSeq.incrementAndGet()
        val base = baseUrl.trimEnd('/')
        val host = base.substringAfter("://", missingDelimiterValue = base)
        return "auth#$seq@$host"
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun urlOf(path: String): String =
        if (baseUrl.endsWith("/") || path.startsWith("/")) "$baseUrl$path"
        else "$baseUrl/$path"

    fun buildRequest(
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

    /**
     * 带 [If-Match] 头的 PATCH/PUT/DELETE 请求构造。`If-Match` 是 V2 服务端必读
     * 的乐观锁头（对应 `shared/server_changes.md` S2）。[ifMatch] 为 null 时
     * 服务端会按"无版本约束"处理,某些端点（如首次创建）允许省略。
     */
    fun buildRequestWithIfMatch(
        method: String,
        path: String,
        ifMatch: Long?,
        body: String?,
    ): Request.Builder {
        val b = buildRequest(method, path, body)
        if (ifMatch != null && ifMatch > 0) {
            b.header("If-Match", ifMatch.toString())
        }
        return b
    }

    @Throws(IOException::class)
    fun execute(req: Request): Response = http.newCall(req).execute()

    fun ensureOk(resp: Response, what: String) {
        if (!resp.isSuccessful) {
            val err = resp.body?.string()?.ifBlank { null } ?: resp.message
            resp.close()
            throw ApiException(resp.code, err, what)
        }
    }

    companion object {
        internal const val TAG = "ServerApi"
    }
}

/**
 * "2xx 返回结果，否则抛 ApiException；404 视为成功（用于 DELETE 幂等）"。
 * [onSuccess] 在 2xx 分支被调用。
 */
internal fun <T> Response.useNot2xxOrOk(what: String, tag: String, onSuccess: (Response) -> T): T {
    return try {
        use { resp ->
            if (resp.isSuccessful) {
                onSuccess(resp)
            } else {
                val err = resp.body?.string()?.ifBlank { null } ?: resp.message
                Log.w(ApiCore.TAG, "[$tag] $what non-2xx: code=${resp.code}")
                throw ApiException(resp.code, err, what)
            }
        }
    } catch (e: ApiException) {
        throw e
    }
}

/**
 * "2xx 走 onSuccess；409 抛 [ConflictException]；其他非 2xx 抛 [ApiException]"。
 */
internal fun <T> Response.useNot2xxOrConflict(what: String, tag: String, onSuccess: (Response) -> T): T {
    return use { resp ->
        when {
            resp.isSuccessful -> onSuccess(resp)
            resp.code == 409 -> {
                val raw = resp.body?.string() ?: "{}"
                Log.w(ApiCore.TAG, "[$tag] $what 409: $raw")
                val obj = runCatching { com.google.gson.JsonParser.parseString(raw).asJsonObject }
                    .getOrElse { com.google.gson.JsonObject() }
                throw ConflictException(ConflictResponse.from(obj), what)
            }
            else -> {
                val err = resp.body?.string()?.ifBlank { null } ?: resp.message
                Log.w(ApiCore.TAG, "[$tag] $what non-2xx: code=${resp.code}")
                throw ApiException(resp.code, err, what)
            }
        }
    }
}
