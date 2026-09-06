package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.data.repository.downloadBytesWithHeaders
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AppInfo.ios"

/**
 * [KMP K16] iOS actual 实接：Ktor Darwin 二进制 GET + ImageCodec.decode
 * （headers 透传——B 站 CDN Referer 头等场景与 Android HttpUtil.downloadBitmap 语义对齐）。
 */
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
