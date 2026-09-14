package top.mcxiafeng.badger.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ResolveRedirect"

suspend fun resolveRedirect(url: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            var currentUrl = url.replace("http://", "https://")
            for (i in 0..5) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                BadgerLog.d(TAG, "重定向[$i]: code=$code, location=${SafeLog.url(location ?: "")}")
                if (code in 301..303 && location != null) {
                    currentUrl = if (location.startsWith("http")) location
                    else URL(currentUrl).toURI().resolve(location).toString()
                } else {
                    break
                }
            }
            if (currentUrl != url.replace("http://", "https://")) currentUrl else null
        } catch (e: Exception) {
            BadgerLog.w(TAG, "短链解析异常: ${e.message}")
            null
        }
    }
}
