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
