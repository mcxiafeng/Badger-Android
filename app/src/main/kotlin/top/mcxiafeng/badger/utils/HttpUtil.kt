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
import okhttp3.Response
import top.mcxiafeng.badger.BadgerApplication
import dagger.hilt.android.EntryPointAccessors
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object HttpUtil {

    private const val DEFAULT_TIMEOUT = 10_000L

    private fun client(timeoutMs: Long = DEFAULT_TIMEOUT): OkHttpClient {
        val base = EntryPointAccessors.fromApplication(
            BadgerApplication.getInstance(), DatabaseEntryPoint::class.java
        ).okHttpClient()
        if (timeoutMs == DEFAULT_TIMEOUT) return base
        return base.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun get(
        urlStr: String,
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlStr).apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }.build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w("HttpUtil", "GET $urlStr → ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "GET $urlStr failed", e)
            null
        }
    }

    suspend fun post(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = body.toRequestBody(contentType.toMediaType())
            val request = Request.Builder().url(urlStr).post(requestBody).apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }.build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w("HttpUtil", "POST $urlStr → ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "POST $urlStr failed", e)
            null
        }
    }

    suspend fun getFinalRedirectUrl(
        urlStr: String,
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt()
    ): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlStr).head().build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "getFinalRedirectUrl $urlStr failed", e)
            null
        }
    }

    fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        val encoded = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return if (baseUrl.contains("?")) "$baseUrl&$encoded" else "$baseUrl?$encoded"
    }

    suspend fun patch(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = body.toRequestBody(contentType.toMediaType())
            val request = Request.Builder().url(urlStr).patch(requestBody).apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }.build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w("HttpUtil", "PATCH $urlStr → ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "PATCH $urlStr failed", e)
            null
        }
    }

    suspend fun put(
        urlStr: String,
        body: String,
        contentType: String = "application/json; charset=UTF-8",
        timeoutMs: Int = DEFAULT_TIMEOUT.toInt(),
        headers: Map<String, String>? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = body.toRequestBody(contentType.toMediaType())
            val request = Request.Builder().url(urlStr).put(requestBody).apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }.build()
            client(timeoutMs.toLong()).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.w("HttpUtil", "PUT $urlStr → ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "PUT $urlStr failed", e)
            null
        }
    }

    suspend fun downloadBitmap(
        urlStr: String,
        headers: Map<String, String>? = null,
        timeoutMs: Long = 10_000
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlStr).apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }.build()
            client(timeoutMs).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } else {
                    Log.w("HttpUtil", "downloadBitmap $urlStr → ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("HttpUtil", "downloadBitmap $urlStr failed", e)
            null
        }
    }
}
