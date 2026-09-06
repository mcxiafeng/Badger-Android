package top.mcxiafeng.badger.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.utils.BadgerLog
import top.mcxiafeng.badger.utils.SafeLog

private const val TAG = "AvatarFetcher.ios"

/** QQ 头像下载超时（对齐 androidMain AvatarFetcher 的 5s 短超时语义）。 */
private const val AVATAR_TIMEOUT_MS = 5_000L

/** 头像 WEBP 编码质量（对齐 androidMain；iOS 侧降级 JPEG，见 ImageCodec.ios 注释）。 */
private const val AVATAR_WEBP_QUALITY = 60

/** 头像下载专用 client（Darwin 引擎；与 /api 传输隔离，避免 401 刷新逻辑误伤 CDN 请求）。 */
private val avatarClient: HttpClient by lazy { HttpClient(Darwin) }

/**
 * [KMP K16] avatarFetcher 的 iOS 实现（对齐 androidMain downloadAndSaveAvatar）：
 * Ktor 下载 QQ 头像字节 → ImageCodec.decode → 缩放 AVATAR_SIZE → 编码（JPEG 降级）→
 * ImageFiles.saveAvatarImage 落盘，返回文件绝对路径；null = 下载/解码失败。
 */
suspend fun downloadAndSaveAvatar(url: String, uin: Long): String? {
    val bytes = downloadBytes(url) ?: run {
        BadgerLog.w(TAG, "头像下载失败: uin=$uin url=${SafeLog.url(url)}")
        return null
    }
    val image = ImageCodec.decode(bytes) ?: run {
        BadgerLog.w(TAG, "头像解码失败: uin=$uin bytes=${bytes.size}")
        return null
    }
    return try {
        val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
        try {
            val encoded = ImageCodec.encodeWebp(scaled, AVATAR_WEBP_QUALITY)
            if (encoded == null) {
                BadgerLog.w(TAG, "头像编码失败: uin=$uin")
                return null
            }
            ImageFiles.saveAvatarImage(encoded, ContactRepositoryImpl.qqAvatarFileName(uin))
        } finally {
            if (scaled !== image) scaled.close()
        }
    } finally {
        image.close()
    }
}

/** Ktor 二进制 GET（AvatarDownload.downloadImage 同引擎，本函数供 avatarFetcher 管线复用）。 */
suspend fun downloadBytes(url: String, timeoutMs: Long = AVATAR_TIMEOUT_MS): ByteArray? {
    return try {
        val response: HttpResponse = avatarClient.get(url) {
            timeout { requestTimeoutMillis = timeoutMs }
        }
        if (!response.status.isSuccess()) {
            BadgerLog.w(TAG, "downloadBytes non-2xx: code=${response.status.value} url=${SafeLog.url(url)}")
            return null
        }
        response.bodyAsBytes()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        BadgerLog.e(TAG, "downloadBytes 失败: ${SafeLog.url(url)}", e)
        null
    }
}

/** B 站 CDN 等带 Referer 头的下载入口（headers 透传）。 */
suspend fun downloadBytesWithHeaders(
    url: String,
    timeoutMs: Long,
    headers: Map<String, String>,
): ByteArray? {
    if (headers.isEmpty()) return downloadBytes(url, timeoutMs)
    return try {
        val response: HttpResponse = avatarClient.get(url) {
            timeout { requestTimeoutMillis = timeoutMs }
            headers.forEach { (k, v) -> header(k, v) }
        }
        if (!response.status.isSuccess()) {
            BadgerLog.w(TAG, "downloadBytesWithHeaders non-2xx: code=${response.status.value} url=${SafeLog.url(url)}")
            return null
        }
        response.bodyAsBytes()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        BadgerLog.e(TAG, "downloadBytesWithHeaders 失败: ${SafeLog.url(url)}", e)
        null
    }
}
