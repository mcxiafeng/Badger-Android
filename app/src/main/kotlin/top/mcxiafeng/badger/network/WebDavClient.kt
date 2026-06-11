package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.di.WebDav

@Singleton
class WebDavClient @Inject constructor(
    @WebDav private val okHttpClient: OkHttpClient
) {
    private val TAG = "Tester"

    private fun authHeader(username: String, password: String): String =
        Credentials.basic(username, password)

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val p = path.trimStart('/')
        return "$base/$p"
    }

    suspend fun testConnection(url: String, username: String, password: String): WebDavResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!NetworkConfig.isAllowInsecureHttp()) {
                    require(url.startsWith("https://")) { "WebDAV URL 必须使用 HTTPS" }
                } else {
                    Log.d(TAG, "testConnection: allowInsecureHttp enabled, skipping HTTPS check")
                }
                val request = Request.Builder()
                    .url(url.trimEnd('/'))
                    .header("Authorization", authHeader(username, password))
                    .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val code = response.code
                response.close()
                Log.d(TAG, "testConnection: code=$code")
                when {
                    code == 207 || response.isSuccessful -> WebDavResult.Success(Unit)
                    code == 401 || code == 403 -> WebDavResult.AuthError("认证失败 (HTTP $code)")
                    code == 404 -> WebDavResult.NotFound
                    else -> WebDavResult.NetworkError(IOException("HTTP $code"))
                }
            } catch (e: SocketTimeoutException) {
                Log.e("Tester", "testConnection timeout: ${e.message}", e)
                WebDavResult.Timeout
            } catch (e: IllegalArgumentException) {
                Log.e("Tester", "testConnection invalid argument: ${e.message}", e)
                WebDavResult.NetworkError(e)
            } catch (e: IOException) {
                Log.e("Tester", "testConnection network error: ${e.message}", e)
                WebDavResult.NetworkError(e)
            }
        }

    suspend fun ensureRemotePath(
        baseUrl: String, username: String, password: String, path: String
    ): WebDavResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkConfig.isAllowInsecureHttp()) {
                require(baseUrl.startsWith("https://")) { "WebDAV URL 必须使用 HTTPS" }
            } else {
                Log.d(TAG, "allowInsecureHttp enabled, skipping HTTPS check")
            }
            val segments = path.trim('/').split('/').filter { it.isNotBlank() }
            var currentPath = ""
            for (segment in segments) {
                currentPath += "/$segment"
                val url = buildUrl(baseUrl, currentPath) + "/"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader(username, password))
                    .method("MKCOL", null)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val code = response.code
                response.close()
                val ok = response.isSuccessful || code == 405 // 405 = already exists
                if (!ok) {
                    Log.e("Tester", "ensureRemotePath: MKCOL $url failed with HTTP $code")
                    return@withContext when {
                        code == 401 || code == 403 -> WebDavResult.AuthError("认证失败 (HTTP $code)")
                        code == 404 -> WebDavResult.NotFound
                        else -> WebDavResult.NetworkError(IOException("HTTP $code"))
                    }
                }
            }
            WebDavResult.Success(Unit)
        } catch (e: SocketTimeoutException) {
            Log.e("Tester", "ensureRemotePath timeout: ${e.message}", e)
            WebDavResult.Timeout
        } catch (e: IllegalArgumentException) {
            Log.e("Tester", "ensureRemotePath invalid argument: ${e.message}", e)
            WebDavResult.NetworkError(e)
        } catch (e: IOException) {
            Log.e("Tester", "ensureRemotePath network error: ${e.message}", e)
            WebDavResult.NetworkError(e)
        }
    }

    suspend fun upload(
        baseUrl: String, username: String, password: String,
        remotePath: String, data: ByteArray
    ): WebDavResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkConfig.isAllowInsecureHttp()) {
                require(baseUrl.startsWith("https://")) { "WebDAV URL 必须使用 HTTPS" }
            } else {
                Log.d(TAG, "allowInsecureHttp enabled, skipping HTTPS check")
            }
            val url = buildUrl(baseUrl, remotePath.trimStart('/'))
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
                .put(data.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            response.close()
            Log.d(TAG, "upload: url=$url, code=$code")
            when {
                response.isSuccessful -> WebDavResult.Success(Unit)
                code == 401 || code == 403 -> WebDavResult.AuthError("认证失败 (HTTP $code)")
                code == 404 -> WebDavResult.NotFound
                else -> WebDavResult.NetworkError(IOException("HTTP $code"))
            }
        } catch (e: SocketTimeoutException) {
            Log.e("Tester", "upload timeout: ${e.message}", e)
            WebDavResult.Timeout
        } catch (e: IllegalArgumentException) {
            Log.e("Tester", "upload invalid argument: ${e.message}", e)
            WebDavResult.NetworkError(e)
        } catch (e: IOException) {
            Log.e("Tester", "upload network error: ${e.message}", e)
            WebDavResult.NetworkError(e)
        }
    }

    suspend fun download(
        baseUrl: String, username: String, password: String, remotePath: String
    ): WebDavResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkConfig.isAllowInsecureHttp()) {
                require(baseUrl.startsWith("https://")) { "WebDAV URL 必须使用 HTTPS" }
            } else {
                Log.d(TAG, "allowInsecureHttp enabled, skipping HTTPS check")
            }
            val url = buildUrl(baseUrl, remotePath.trimStart('/'))
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (!response.isSuccessful) {
                response.close()
                Log.e("Tester", "download: failed with HTTP $code")
                return@withContext when {
                    code == 401 || code == 403 -> WebDavResult.AuthError("认证失败 (HTTP $code)")
                    code == 404 -> WebDavResult.NotFound
                    else -> WebDavResult.NetworkError(IOException("HTTP $code"))
                }
            }
            val bytes = response.body?.bytes()
            response.close()
            if (bytes != null) {
                WebDavResult.Success(bytes)
            } else {
                WebDavResult.NetworkError(IOException("Response body is null"))
            }
        } catch (e: SocketTimeoutException) {
            Log.e("Tester", "download timeout: ${e.message}", e)
            WebDavResult.Timeout
        } catch (e: IllegalArgumentException) {
            Log.e("Tester", "download invalid argument: ${e.message}", e)
            WebDavResult.NetworkError(e)
        } catch (e: IOException) {
            Log.e("Tester", "download network error: ${e.message}", e)
            WebDavResult.NetworkError(e)
        }
    }

    data class RemoteFileInfo(val name: String, val size: Long, val lastModified: Long?)

    suspend fun listFiles(
        baseUrl: String, username: String, password: String, remotePath: String
    ): WebDavResult<List<RemoteFileInfo>> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkConfig.isAllowInsecureHttp()) {
                require(baseUrl.startsWith("https://")) { "WebDAV URL 必须使用 HTTPS" }
            } else {
                Log.d(TAG, "allowInsecureHttp enabled, skipping HTTPS check")
            }
            val url = buildUrl(baseUrl, remotePath.trimStart('/'))
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
                |<d:propfind xmlns:d="DAV:">
                |  <d:prop><d:getcontentlength/><d:getlastmodified/></d:prop>
                |</d:propfind>""".trimMargin()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
                .header("Depth", "1")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (code != 207) {
                response.close()
                return@withContext when {
                    code == 401 || code == 403 -> WebDavResult.AuthError("认证失败 (HTTP $code)")
                    code == 404 -> WebDavResult.NotFound
                    else -> WebDavResult.NetworkError(IOException("PROPFIND returned HTTP $code"))
                }
            }
            val body = response.body?.string()
            response.close()
            if (body == null) {
                return@withContext WebDavResult.NetworkError(IOException("Response body is null"))
            }
            // Parse simple XML response for file names and sizes
            val files = mutableListOf<RemoteFileInfo>()
            val hrefRegex = Regex("<d:href>([^<]+)</d:href>")
            val sizeRegex = Regex("<d:getcontentlength>([^<]+)</d:getcontentlength>")
            val modifiedRegex = Regex("<d:getlastmodified>([^<]+)</d:getlastmodified>")
            val responses = body.split("<d:response>")
            for (resp in responses.drop(1)) {
                val href = hrefRegex.find(resp)?.groupValues?.getOrNull(1) ?: continue
                val name = href.trimEnd('/').substringAfterLast('/')
                if (name.isBlank() || name == remotePath.trim('/').substringAfterLast('/')) continue
                val size = sizeRegex.find(resp)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val modified = modifiedRegex.find(resp)?.groupValues?.getOrNull(1)
                files.add(RemoteFileInfo(name, size, modified?.let { parseHttpDate(it) }))
            }
            WebDavResult.Success(files.sortedByDescending { it.name })
        } catch (e: SocketTimeoutException) {
            Log.e("Tester", "listFiles timeout: ${e.message}", e)
            WebDavResult.Timeout
        } catch (e: IllegalArgumentException) {
            Log.e("Tester", "listFiles invalid argument: ${e.message}", e)
            WebDavResult.NetworkError(e)
        } catch (e: IOException) {
            Log.e("Tester", "listFiles network error: ${e.message}", e)
            WebDavResult.NetworkError(e)
        }
    }

    private fun parseHttpDate(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            Log.e("Tester", "parseHttpDate failed: $dateStr", e)
            0L
        }
    }
}
