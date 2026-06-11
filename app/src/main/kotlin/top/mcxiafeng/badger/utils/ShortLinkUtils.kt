package top.mcxiafeng.badger.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.ocr.SHORT_LINK_DOMAINS
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private const val TAG = "ShortLinkUtils"

/**
 * 判断是否为已知短链域名
 */
fun isShortLink(url: String): Boolean {
    val host = try { URI(url).host?.lowercase() ?: return false } catch (e: Exception) { Log.w("ShortLinkUtils", "URI解析失败: $url", e); return false }
    return SHORT_LINK_DOMAINS.containsKey(host)
}

/**
 * 跟随重定向获取最终 URL，超时 3s，最多 5 次跳转
 */
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
                Log.d(TAG, "重定向[$i]: code=$code, location=$location")
                if (code in 301..303 && location != null) {
                    currentUrl = if (location.startsWith("http")) location
                    else URL(currentUrl).toURI().resolve(location).toString()
                } else {
                    break
                }
            }
            if (currentUrl != url.replace("http://", "https://")) currentUrl else null
        } catch (e: Exception) {
            Log.w(TAG, "短链解析异常: ${e.message}")
            null
        }
    }
}
