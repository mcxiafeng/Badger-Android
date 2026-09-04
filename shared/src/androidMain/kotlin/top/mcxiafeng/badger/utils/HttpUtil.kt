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

/**
 * Android HTTP 工具（OkHttp 底座）。
 *
 * [KMP K06] 已从 app 主源集迁入 shared androidMain：OkHttp 是 Android 单端传输层
 * （Q2 裁决：OkHttp 无 iOS native 变体），common 侧对应物是 [KtorHttpCore]。
 * Bitmap 解码 / downloadBitmap 留在本层（android.graphics 平台 API）。
 *
 * OkHttpClient 由 Koin 提供——androidMain 不依赖 Koin，通过 [clientProvider]
 * 注入（BadgerApplication/KoinModules 启动时 set，测试可换）。
 */
object HttpUtil {

    private const val TAG = "HttpUtil"
    private const val DEFAULT_TIMEOUT = 10_000L

    /** OkHttpClient 提供器；App 启动时注入 Koin factory，避免 androidMain 依赖 Koin。 */
    @Volatile
    lateinit var clientProvider: () -> OkHttpClient

    private fun client(timeoutMs: Long = DEFAULT_TIMEOUT): OkHttpClient {
        val base = clientProvider()
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
            Log.w(TAG, "request timeout: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.TIMEOUT,
            )
        } catch (e: IOException) {
            Log.w(TAG, "request network failure: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.NETWORK,
            )
        } catch (e: Exception) {
            Log.e(TAG, "request failed: ${SafeLog.url(request.url.toString())}", e)
            HttpResult.Failure(
                code = 0,
                body = null,
                errorType = HttpResult.ErrorType.UNKNOWN,
            )
        }
    }

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
            Log.e(TAG, "getFinalRedirectUrl ${SafeLog.url(urlStr)} failed", e)
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
                    Log.w(TAG, "downloadBitmap ${SafeLog.url(urlStr)} → ${response.code}")
                    return@use null
                }
                response.body?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadBitmap ${SafeLog.url(urlStr)} failed", e)
            null
        }
    }
}
