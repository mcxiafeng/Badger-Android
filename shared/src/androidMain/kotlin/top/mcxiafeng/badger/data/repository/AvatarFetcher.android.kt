package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.BadgerLog

private const val AVATAR_TIMEOUT_MS = 5_000L

suspend fun downloadAndSaveAvatar(url: String, uin: Long): String? {
    val bitmap = HttpUtil.downloadBitmap(url, timeoutMs = AVATAR_TIMEOUT_MS) ?: return null
    val image = PlatformImage(bitmap)
    try {
        val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
        try {
            val bytes = ImageCodec.encodeWebp(scaled, AVATAR_WEBP_QUALITY)
            if (bytes == null) {
                BadgerLog.w("AvatarFetcher", "头像编码失败: $uin")
                return null
            }
            return ImageFiles.saveAvatarImage(bytes, ContactRepositoryImpl.qqAvatarFileName(uin))
        } finally {
            if (scaled !== image) scaled.close()
        }
    } finally {
        image.close()
    }
}

private const val AVATAR_WEBP_QUALITY = 60
