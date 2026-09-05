package top.mcxiafeng.badger.platform

import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "AppInfo.ios"

/** [KMP K13c] iOS actual 骨架：KtorHttpCore GET + ImageCodec.decode（K16 接线）。 */
actual suspend fun downloadImage(
    url: String,
    timeoutMs: Long,
    headers: Map<String, String>,
): PlatformImage? {
    BadgerLog.w(TAG, "downloadImage: iOS 骨架未接线（K16 KtorHttpCore）: $url")
    return null
}
