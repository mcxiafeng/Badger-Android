package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AppInfo.android"

/** [KMP K13c] Android actual：HttpUtil.downloadBitmap（Bitmap 回收语义由 PlatformImage 承接）。 */
actual suspend fun downloadImage(
    url: String,
    timeoutMs: Long,
    headers: Map<String, String>,
): PlatformImage? {
    val bitmap = HttpUtil.downloadBitmap(url, timeoutMs = timeoutMs, headers = headers) ?: return null
    BadgerLog.d(TAG, "downloadImage: ${SafeLogUrlLite.mask(url)} (${bitmap.width}x${bitmap.height})")
    return PlatformImage(bitmap)
}

private object SafeLogUrlLite {
    fun mask(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return "***"
        val rest = url.substring(schemeEnd + 3)
        val pathStart = rest.indexOf('/')
        val host = if (pathStart < 0) rest else rest.substring(0, pathStart)
        return url.take(schemeEnd) + "://" + host + "/***"
    }
}
