package top.mcxiafeng.badger.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

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
