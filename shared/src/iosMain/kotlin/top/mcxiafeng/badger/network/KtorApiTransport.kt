package top.mcxiafeng.badger.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Headers
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import top.mcxiafeng.badger.shared.util.nowMs
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog

private const val TAG = "KtorApiTransport"

/**
 * [KMP K16] [ApiTransport] 的 Ktor 实现（iOS，Darwin 引擎）。
 *
 * 阻塞语义对齐 Android OkHttp 路径（ServerApi 契约本身阻塞，调用方均在
 * BadgerDispatchers.io 上）：[execute] 内 withContext(IO) + runBlocking。
 *
 * 401 处理平移 Android `NetworkModule.tokenRefreshInterceptor` 语义：
 * - 收到 401 → 调 [onUnauthorized]（注入的刷新回调，内含双重检查 + 互斥）；
 * - 回调返回新 token（服务端接受）→ 用新 token 重试原请求**一次**；
 * - 返回 null（服务端拒绝或网络不可达）→ 抛 `ApiException(401)`（不透传 401 响应），
 *   凭证是否清除由回调内裁决（网络瞬时故障保凭证，服务端拒绝清凭证）。
 *
 * 差异登记（vs OkHttp，真机验收 K17）：
 * - 超时：OkHttp connect 15s / read 15s / write 30s 三段 → Ktor 单一请求超时 30s（保守并集）；
 * - 连接失败抛 Ktor 异常体系（非 java.io.IOException）——调用方均按「非 ApiException = 网络错误」
 *   处理，语义等价。
 */
class KtorApiTransport(
    engine: HttpClientEngine? = null,
    private val onUnauthorized: (suspend (failedToken: String) -> String?)? = null,
) : ApiTransport {

    private val client: HttpClient = if (engine != null) HttpClient(engine) else HttpClient(Darwin)

    override fun execute(request: ApiHttpRequest): ApiHttpResponse = kotlinx.coroutines.runBlocking {
        val response = send(request, overrideAuth = null)
        if (response.code != 401) return@runBlocking response

        val refresher = onUnauthorized ?: return@runBlocking response
        val failedToken = request.headers[AUTH_HEADER]?.removePrefix(BEARER_PREFIX).orEmpty()
        BadgerLog.w(TAG, "401 on ${request.method} ${SafeLog.url(request.url)}, refreshing token")
        val newToken = refresher(failedToken)
            ?: throw ApiException(401, "token refresh failed", request.url.substringAfter("/api/", request.url))
        send(request, overrideAuth = newToken)
    }

    private suspend fun send(request: ApiHttpRequest, overrideAuth: String?): ApiHttpResponse {
        // map merge 覆盖 Authorization（ktor header() 是 append 语义，不能直接二次追加）
        val headers = if (overrideAuth != null) {
            request.headers + mapOf(AUTH_HEADER to "$BEARER_PREFIX$overrideAuth")
        } else {
            request.headers
        }
        val started = nowMs()
        return try {
            val response: HttpResponse = client.request(request.url) {
                this.method = HttpMethod.parse(request.method)
                headers.forEach { (k, v) -> header(k, v) }
                request.multipart?.let { part ->
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append(
                                    part.fieldName,
                                    part.bytes,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, part.mediaType)
                                        append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName}\"")
                                    },
                                )
                            },
                        ),
                    )
                } ?: request.body?.let { body ->
                    setBody(body)
                    contentType(ContentType.Application.Json)
                }
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
            }
            val text = response.bodyAsText()
            BadgerLog.d(
                TAG,
                "${request.method} ${request.url.substringAfter("/api/")} -> " +
                    "${response.status.value} (${nowMs() - started}ms, ${text.length}B)",
            )
            ApiHttpResponse(response.status.value, response.status.description, text)
        } catch (e: Throwable) {
            BadgerLog.e(TAG, "${request.method} ${SafeLog.url(request.url)} failed: ${e::class.simpleName}: ${e.message}", e)
            throw e
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val AUTH_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
