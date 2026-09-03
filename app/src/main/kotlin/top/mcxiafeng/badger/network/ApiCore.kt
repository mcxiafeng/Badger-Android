package top.mcxiafeng.badger.network

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * 各 API 域共享的 HTTP 基础设施。
 * baseUrl 可变以支持运行时换服务地址。
 */
class ApiCore(
    @Volatile var baseUrl: String,
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
) {
    private val callSeq = AtomicLong(0)

    fun nextCallTag(): String {
        val seq = callSeq.incrementAndGet()
        val base = baseUrl.trimEnd('/')
        val host = base.substringAfter("://", missingDelimiterValue = base)
        return "auth#$seq@$host"
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Join an API path without double slash. */
    fun urlOf(path: String): String {
        val trimmed = baseUrl.trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw ApiException(0, "服务器地址缺少协议前缀（http/https）: $trimmed", "baseUrl")
        }
        return "${trimmed}/${path.trimStart('/')}"
    }

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

    @Throws(IOException::class)
    fun execute(req: Request): Response = http.newCall(req).execute()

    /**
     * Build a multipart upload request. The server contract currently uses
     * field name `file` for `POST /api/user/upload`.
     */
    fun buildMultipartRequest(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mediaType: String,
        fieldName: String = "file",
    ): Request.Builder {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                fieldName,
                fileName,
                fileBytes.toRequestBody(mediaType.toMediaType()),
            )
            .build()
        val b = Request.Builder()
            .url(urlOf(path))
            .post(body)
        tokenProvider()?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

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
 * 解析 ApiResult 壳 `{code:200, message, data}`，非 200 抛 ApiException。
 */
internal fun <T> Response.unwrapApiResult(what: String, tag: String, onData: (JsonElement) -> T): T {
    return use { resp ->
        if (!resp.isSuccessful) {
            val err = resp.body?.string()?.ifBlank { null } ?: resp.message
            Log.w(ApiCore.TAG, "[$tag] $what non-2xx: code=${resp.code}")
            throw ApiException(resp.code, err, what)
        }
        val body = resp.body?.string() ?: "{}"
        val root = try {
            JsonParser.parseString(body)
        } catch (e: JsonSyntaxException) {
            Log.w(ApiCore.TAG, "[$tag] $what malformed JSON: ${body.take(200)}")
            throw ApiException(resp.code, body, "$what malformed JSON")
        }
        if (!root.isJsonObject) {
            Log.w(ApiCore.TAG, "[$tag] $what expected ApiResult object, got ${root.javaClass.simpleName}")
            throw ApiException(resp.code, body, "$what not an ApiResult object")
        }
        val obj = root.asJsonObject
        val codeElement = obj.get("code")?.takeIf { !it.isJsonNull }
        if (codeElement != null && !(codeElement.isJsonPrimitive && codeElement.asJsonPrimitive.isNumber)) {
            Log.w(ApiCore.TAG, "[$tag] $what ApiResult code 非数值: ${codeElement.toString().take(50)}")
            throw ApiException(resp.code, body, "$what ApiResult code is not a number")
        }
        val code = codeElement?.asInt
        if (code != null && code != 200) {
            val msg = obj.get("message")?.takeIf { !it.isJsonNull }?.asString
            Log.w(ApiCore.TAG, "[$tag] $what ApiResult code=$code msg=$msg")
            throw ApiException(code, msg, what)
        }
        val data = obj.get("data")
        if (data == null || data.isJsonNull) {
            Log.w(ApiCore.TAG, "[$tag] $what ApiResult missing/null data: $body")
            onData(JsonNull.INSTANCE)
        } else {
            onData(data)
        }
    }
}
