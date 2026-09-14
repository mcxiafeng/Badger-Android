package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.data.repository.downloadBytesWithHeaders
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AppInfo.ios"

actual suspend fun downloadImage(
    url: String,
    timeoutMs: Long,
    headers: Map<String, String>,
): PlatformImage? {
    val bytes = downloadBytesWithHeaders(url, timeoutMs, headers) ?: return null
    return ImageCodec.decode(bytes) ?: run {
        BadgerLog.w(TAG, "downloadImage: decode 失败 bytes=${bytes.size} url=$url")
        null
    }
}
