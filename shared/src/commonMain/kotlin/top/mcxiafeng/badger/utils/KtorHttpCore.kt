package top.mcxiafeng.badger.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.head
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * [KMP K06] HttpUtil 拆分的 common 侧：Ktor 请求语义层（Q2 裁决选型）。
 *
 * 引擎由平台注入：Android=CIO（androidMain）/ iOS=Darwin。
 * Android 现行路径仍走 OkHttp（HttpUtil），本类是网络逻辑进 commonMain 的统一底座
 * ——K07+ 数据/同步层迁 common 时直接复用。
 *
 * 错误分类与 Android OkHttp 路径 ([HttpUtil]) 语义对齐：
 * - 超时 → TIMEOUT；401/403 → AUTH；429 → RATE_LIMIT；5xx → SERVER；其他 4xx → OTHER。
 */
class KtorHttpCore(
    engine: HttpClientEngine? = null,
    private val defaultTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val client: HttpClient = if (engine != null) HttpClient(engine) else HttpClient()

    suspend fun request(
        method: HttpMethod,
        url: String,
        body: String? = null,
        contentType: String? = CONTENT_TYPE_JSON,
        timeoutMs: Long = defaultTimeoutMs,
        headers: Map<String, String>? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        try {
            val response = client.request(url) {
                this.method = method
                headers?.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    setBody(body)
                    contentType?.let { contentType(parseContentType(it)) }
                }
                timeout { requestTimeoutMillis = timeoutMs }
            }
            finish(url, response)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "request failed: ${SafeLog.url(url)}", e)
            HttpResult.Failure(0, null, HttpResult.ErrorType.NETWORK)
        }
    }

    suspend fun get(url: String, timeoutMs: Long = defaultTimeoutMs, headers: Map<String, String>? = null): HttpResult =
        request(HttpMethod.Get, url, timeoutMs = timeoutMs, headers = headers)

    suspend fun post(url: String, body: String, timeoutMs: Long = defaultTimeoutMs, headers: Map<String, String>? = null): HttpResult =
        request(HttpMethod.Post, url, body = body, timeoutMs = timeoutMs, headers = headers)

    suspend fun put(url: String, body: String, timeoutMs: Long = defaultTimeoutMs, headers: Map<String, String>? = null): HttpResult =
        request(HttpMethod.Put, url, body = body, timeoutMs = timeoutMs, headers = headers)

    suspend fun patch(url: String, body: String, timeoutMs: Long = defaultTimeoutMs, headers: Map<String, String>? = null): HttpResult =
        request(HttpMethod.Patch, url, body = body, timeoutMs = timeoutMs, headers = headers)

    /** HEAD 请求跟随后返回最终 URL（对齐 OkHttp getFinalRedirectUrl 语义）。 */
    suspend fun getFinalRedirectUrl(url: String, timeoutMs: Long = defaultTimeoutMs): String? =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.head(url) {
                    timeout { requestTimeoutMillis = timeoutMs }
                }
                response.call.request.url.toString()
            } catch (e: Exception) {
                BadgerLog.e(TAG, "getFinalRedirectUrl ${SafeLog.url(url)} failed", e)
                null
            }
        }

    /** 对齐 HttpUtil.buildUrl 的 query 拼接语义（URLEncoder + UTF-8，空格 → +）。 */
    fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val encoded = params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return if (baseUrl.contains("?")) "$baseUrl&$encoded" else "$baseUrl?$encoded"
    }

    private suspend fun finish(url: String, response: HttpResponse): HttpResult {
        val code = response.status.value
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            HttpResult.Success(text)
        } else {
            val errorType = when {
                code == 401 || code == 403 -> HttpResult.ErrorType.AUTH
                code == 429 -> HttpResult.ErrorType.RATE_LIMIT
                code in SERVER_ERROR_RANGE -> HttpResult.ErrorType.SERVER
                code in CLIENT_ERROR_RANGE -> HttpResult.ErrorType.OTHER
                else -> HttpResult.ErrorType.UNKNOWN
            }
            HttpResult.Failure(code = code, body = text, errorType = errorType)
        }
    }

    private fun parseContentType(raw: String): io.ktor.http.ContentType =
        io.ktor.http.ContentType.parse(raw)

    companion object {
        private const val TAG = "KtorHttpCore"
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val CONTENT_TYPE_JSON = "application/json; charset=UTF-8"
        private val SERVER_ERROR_RANGE = 500..599
        private val CLIENT_ERROR_RANGE = 400..499
        private val HEX = "0123456789ABCDEF".toCharArray()

        /** java.net.URLEncoder.encode(v, "UTF-8") 等价实现（common 无 java.net；空格→+，保留 .-*_） */
        fun urlEncode(value: String): String {
            val out = StringBuilder()
            val bytes = value.encodeToByteArray()
            for (byte in bytes) {
                val b = byte.toInt() and 0xFF
                when {
                    b in 'a'.code..'z'.code || b in 'A'.code..'Z'.code || b in '0'.code..'9'.code ||
                        b == '.'.code || b == '-'.code || b == '*'.code || b == '_'.code ->
                        out.append(b.toChar())
                    b == ' '.code -> out.append('+')
                    else -> out.append('%')
                        .append(HEX[(b shr 4) and 0xF])
                        .append(HEX[b and 0xF])
                }
            }
            return out.toString()
        }
    }
}
