package top.mcxiafeng.badger.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.mcxiafeng.badger.di.DatabaseEntryPoint
import top.mcxiafeng.badger.BadgerApplication
import dagger.hilt.android.EntryPointAccessors

object WebDavClient {
    private const val TAG = "WebDavClient"

    private fun client(): OkHttpClient =
        EntryPointAccessors.fromApplication(
            BadgerApplication.getInstance(), DatabaseEntryPoint::class.java
        ).okHttpClient()

    private fun authHeader(username: String, password: String): String =
        Credentials.basic(username, password)

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val p = path.trimStart('/')
        return "$base/$p"
    }

    suspend fun testConnection(url: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url.trimEnd('/'))
                    .header("Authorization", authHeader(username, password))
                    .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                    .header("Depth", "0")
                    .build()
                val response = client().newCall(request).execute()
                val success = response.isSuccessful || response.code == 207
                response.close()
                Log.d(TAG, "testConnection: code=${response.code}, success=$success")
                success
            } catch (e: Exception) {
                Log.e(TAG, "testConnection failed: ${e.message}")
                false
            }
        }

    suspend fun ensureRemotePath(
        baseUrl: String, username: String, password: String, path: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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
                val response = client().newCall(request).execute()
                val ok = response.isSuccessful || response.code == 405 // 405 = already exists
                response.close()
                if (!ok) {
                    Log.e(TAG, "ensureRemotePath: MKCOL $url failed with ${response.code}")
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureRemotePath failed: ${e.message}")
            false
        }
    }

    suspend fun upload(
        baseUrl: String, username: String, password: String,
        remotePath: String, data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(baseUrl, remotePath.trimStart('/'))
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
                .put(data.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            val response = client().newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            Log.d(TAG, "upload: url=$url, code=${response.code}, success=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "upload failed: ${e.message}")
            false
        }
    }

    suspend fun download(
        baseUrl: String, username: String, password: String, remotePath: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(baseUrl, remotePath.trimStart('/'))
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(username, password))
                .get()
                .build()
            val response = client().newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                Log.e(TAG, "download: failed with ${response.code}")
                return@withContext null
            }
            val bytes = response.body?.bytes()
            response.close()
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "download failed: ${e.message}")
            null
        }
    }

    data class RemoteFileInfo(val name: String, val size: Long, val lastModified: Long?)

    suspend fun listFiles(
        baseUrl: String, username: String, password: String, remotePath: String
    ): List<RemoteFileInfo>? = withContext(Dispatchers.IO) {
        try {
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
            val response = client().newCall(request).execute()
            if (response.code != 207) {
                response.close()
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null
            response.close()
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
            files.sortedByDescending { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "listFiles failed: ${e.message}")
            null
        }
    }

    private fun parseHttpDate(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.ENGLISH)
            sdf.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
