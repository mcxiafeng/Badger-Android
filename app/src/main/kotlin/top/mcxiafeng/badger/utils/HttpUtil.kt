package top.mcxiafeng.badger.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object HttpUtil {

    private const val DEFAULT_TIMEOUT = 10_000L

    private fun client(timeoutMs: Long = DEFAULT_TIMEOUT): OkHttpClient {
        val base = org.koin.core.context.GlobalContext.get().get<OkHttpClient>()
        if (timeoutMs == DEFAULT_TIMEOUT) return base
        return base.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun executeResult(request: Request, timeoutMs: Long): HttpResult {
        return try {
            client(timeoutMs).newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    HttpResult.Success(body ?: "")
                } else {
                    val errorType = when {
                        response.code == 401 || response.code == 403 -> HttpResult.ErrorType.AUTH
                        response.code == 429 -> HttpResult.ErrorType.RATE_LIMIT
                        response.code in 500..599 -> HttpResult.ErrorType.SERVER
                        response.code in 400..499 -> HttpResult.ErrorType.OTHER
                        else -> HttpResult.ErrorType.UNKNOWN
                    }
                    HttpResult.Failure(
                        code = response.code,
                        body = body,
                        errorType = errorType,
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w("HttpUtil", "request timeout: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.TIMEOUT,
            )
        } catch (e: IOException) {
            Log.w("HttpUtil", "request network failure: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.NETWORK,
            )
        } catch (e: Exception) {
            Log.e("HttpUtil", "request failed: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.UNKNOWN,
            )
        }
    }

    // ---------------- Result 版（结构化错误）----------------

    suspend fun postResult(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .post(body.toRequestBody(contentType.toMediaType()))
            .apply { headers?.forEach { (k, v) -> header(k, v) } }
            .build()
        executeResult(request, timeoutMs.toLong())
    }

    suspend fun getResult(
        urlStr: String,
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .apply { headers?.forEach { (k, v) -> header(k, v) } }
            .build()
        executeResult(request, timeoutMs.toLong())
    }

    suspend fun patchResult(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .patch(body.toRequestBody(contentType.toMediaType()))
            .apply { headers?.forEach { (k, v) -> header(k, v) } }
            .build()
        executeResult(request, timeoutMs.toLong())
    }

    suspend fun putResult(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlStr)
            .put(body.toRequestBody(contentType.toMediaType()))
            .apply { headers?.forEach { (k, v) -> header(k, v) } }
            .build()
        executeResult(request, timeoutMs.toLong())
    }

    /**
     * 便捷包装：失败时 throw [HttpException] 带 [HttpResult.ErrorType]，
     * 适合只关心"成功拿到 body / 拿到错误异常"的调用方。
     */
    suspend fun postOrThrow(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null,
    ): String {
        return when (val result = postResult(urlStr, body, contentType, timeoutMs, headers)) {
            is HttpResult.Success -> result.body
            is HttpResult.Failure -> throw HttpException(
                code = result.code,
                errorType = result.errorType,
                responseBody = result.body,
                message = "POST ${SafeLog.url(urlStr)} → ${result.code} (${result.errorType})",
            )
        }
    }

    suspend fun getFinalRedirectUrl(
        urlStr: String,
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlStr).head().build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "getFinalRedirectUrl ${SafeLog.url(urlStr)} failed", e)
            null
        }
    }

    fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val encoded = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return if (baseUrl.contains("?")) "$baseUrl&$encoded" else "$baseUrl?$encoded"
    }

    suspend fun downloadBitmap(
        urlStr: String,
        headers: Map<String, String>? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT,
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(urlStr)
                .apply { headers?.forEach { (k, v) -> header(k, v) } }
                .build()
            client(timeoutMs).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("HttpUtil", "downloadBitmap ${SafeLog.url(urlStr)} → ${response.code}")
                    return@use null
                }
                response.body?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "downloadBitmap ${SafeLog.url(urlStr)} failed", e)
            null
        }
    }
}
