package top.mcxiafeng.badger.network

import kotlinx.atomicfu.atomic
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.mcxiafeng.badger.utils.BadgerLog

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

    
    fun urlOf(path: String): String {
        val trimmed = baseUrl.trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw ApiException(0, "服务器地址缺少协议前缀（http/https）: $trimmed", "baseUrl")
        }
        return "${trimmed}/${path.trimStart('/')}"
    }

    

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
