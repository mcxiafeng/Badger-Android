package top.mcxiafeng.badger.network

import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 各 API 域共享的 HTTP 基础设施（[KMP K16] 自 androidMain 上移 commonMain，
 * OkHttp 类型解耦为 [ApiTransport]——Android=OkHttpApiTransport 原路径，iOS=KtorApiTransport）。
 *
 * baseUrl 可变以支持运行时换服务地址。
 */
class ApiCore(
    @kotlin.concurrent.Volatile var baseUrl: String,
    private val transport: ApiTransport,
    private val tokenProvider: () -> String?,
) {
    private val callSeq = atomic(0L)

    fun nextCallTag(): String {
        val seq = callSeq.incrementAndGet()
        val base = baseUrl.trimEnd('/')
        val host = base.substringAfter("://", missingDelimiterValue = base)
        return "auth#$seq@$host"
    }

    /** Join an API path without double slash. */
    fun urlOf(path: String): String {
        val trimmed = baseUrl.trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw ApiException(0, "服务器地址缺少协议前缀（http/https）: $trimmed", "baseUrl")
        }
        return "${trimmed}/${path.trimStart('/')}"
    }

    /**
     * 构造 API 请求（原 `buildRequest(...).build()` 的中立形态）。
     * token 头在此统一注入；body 缺省补 "{}" 的语义由各传输层按方法补齐（对齐原 OkHttp 行为）。
     */
    fun request(
        method: String,
        path: String,
        body: String? = null,
    ): ApiHttpRequest {
        val headers = tokenProvider()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        return ApiHttpRequest(
            method = method,
            url = urlOf(path),
            body = body,
            headers = headers,
        )
    }

    /**
     * Multipart 上传请求。服务端契约：`POST /api/user/upload` 固定字段名 `file`。
     */
    fun multipartRequest(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mediaType: String,
        fieldName: String = "file",
    ): ApiHttpRequest {
        val headers = tokenProvider()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        return ApiHttpRequest(
            method = "POST",
            url = urlOf(path),
            headers = headers,
            multipart = ApiMultipartPart(fieldName, fileName, fileBytes, mediaType),
        )
    }

    fun execute(req: ApiHttpRequest): ApiHttpResponse = transport.execute(req)

    fun ensureOk(resp: ApiHttpResponse, what: String) {
        if (resp.code !in 200..299) {
            val err = resp.bodyText?.ifBlank { null } ?: resp.message
            throw ApiException(resp.code, err, what)
        }
    }

    companion object {
        internal const val TAG = "ServerApi"
    }
}

/**
 * 解析 ApiResult 壳 `{code:200, message, data}`，非 200 抛 ApiException。
 *
 * [K04] 解析器 Gson → kotlinx.serialization：data 元素以 kotlinx [JsonElement] 透传给
 * [onData]（各 Api 子客户端用 `BadgerJson.decodeFromJsonElement` 或手写 from(JsonObject) 消费）。
 * [KMP K16] 挂载点从 OkHttp Response 改为中立 [ApiHttpResponse]（body 已由传输层一次性读出）。
 */
fun <T> ApiHttpResponse.unwrapApiResult(what: String, tag: String, onData: (JsonElement) -> T): T {
    if (code !in 200..299) {
        val err = bodyText?.ifBlank { null } ?: message
        BadgerLog.w(ApiCore.TAG, "[$tag] $what non-2xx: code=$code")
        throw ApiException(code, err, what)
    }
    val body = bodyText ?: "{}"
    val root: JsonElement = try {
        BadgerJson.parseToJsonElement(body)
    } catch (e: Exception) {
        BadgerLog.w(ApiCore.TAG, "[$tag] $what malformed JSON: ${body.take(200)}")
        throw ApiException(code, body, "$what malformed JSON")
    }
    val obj = root as? JsonObject
    if (obj == null) {
        BadgerLog.w(ApiCore.TAG, "[$tag] $what expected ApiResult object, got ${root::class.simpleName}")
        throw ApiException(code, body, "$what not an ApiResult object")
    }
    val codeElement = obj["code"]?.takeIf { it !is JsonNull }
    if (codeElement != null && !codeElement.isNumberPrimitive()) {
        BadgerLog.w(ApiCore.TAG, "[$tag] $what ApiResult code 非数值: ${codeElement.toString().take(50)}")
        throw ApiException(code, body, "$what ApiResult code is not a number")
    }
    val apiCode = codeElement?.let { intOr(it, 0) }
    if (apiCode != null && apiCode != 200) {
        val msg = (obj["message"] as? JsonPrimitive)?.content
        BadgerLog.w(ApiCore.TAG, "[$tag] $what ApiResult code=$apiCode msg=$msg")
        throw ApiException(apiCode, msg, what)
    }
    val data = obj["data"]
    if (data == null || data is JsonNull) {
        BadgerLog.w(ApiCore.TAG, "[$tag] $what ApiResult missing/null data: $body")
        return onData(JsonNull)
    }
    return onData(data)
}
