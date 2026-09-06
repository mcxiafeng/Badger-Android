package top.mcxiafeng.badger.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [KMP K16] [ApiTransport] 的 OkHttp 实现（Android）。
 *
 * 语义与原 androidMain ApiCore.buildRequest/execute 逐项对齐：
 * - POST/PATCH/PUT：body 缺省补 "{}"，JSON media type（charset=utf-8）；
 * - GET/DELETE：无请求体；
 * - multipart：FORM 类型 + 单 part（uploadImage 路径）；
 * - Authorization 头由 ApiCore 统一放入 [ApiHttpRequest.headers]（header() 语义 = 覆盖同名）；
 * - 超时/重定向/缓存/UA 均沿用注入的 OkHttpClient 配置（app NetworkModule 构造，行为零变化）。
 */
class OkHttpApiTransport(private val http: OkHttpClient) : ApiTransport {

    override fun execute(request: ApiHttpRequest): ApiHttpResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        when (request.method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post(requestBody(request))
            "PATCH" -> builder.patch(requestBody(request))
            "PUT" -> builder.put(requestBody(request))
            else -> error("unsupported method ${request.method}")
        }
        return http.newCall(builder.build()).execute().use { resp ->
            ApiHttpResponse(resp.code, resp.message, resp.body?.string())
        }
    }

    private fun requestBody(request: ApiHttpRequest): RequestBody {
        request.multipart?.let { part ->
            return MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    part.fieldName,
                    part.fileName,
                    part.bytes.toRequestBody(part.mediaType.toMediaType()),
                )
                .build()
        }
        return (request.body ?: "{}").toRequestBody(JSON_MEDIA)
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
