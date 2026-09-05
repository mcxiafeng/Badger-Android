package top.mcxiafeng.badger.platform

import platform.Foundation.NSData
import platform.UIKit.UIImage
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageCodec.ios"

/**
 * [KMP K13c] iOS actual：
 * - decode：UIImage(data:)（JPEG/PNG representation 的 K/N 绑定名随版本变动，
 *   编码半边 K16 统一接 CoreGraphics 光栅化，当前返回 null 走调用方降级）；
 * - scaleToMaxSide：UIGraphics 重绘（K16 接线，当前原样返回）。
 */
actual object ImageCodec {

    actual fun decode(bytes: ByteArray): PlatformImage? {
        if (bytes.isEmpty()) return null
        return try {
            val data: NSData = bytes.toNSData()
            val image = UIImage(data = data) ?: return null
            PlatformImage(image)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "decode 失败 (${bytes.size} bytes)", e)
            null
        }
    }

    actual fun encodeWebp(image: PlatformImage, quality: Int): ByteArray? {
        // K16：CoreGraphics 光栅化 → jpegData；iOS 无 WebP 系统编解码器
        BadgerLog.w(TAG, "encodeWebp: iOS 骨架未接线（K16）")
        return null
    }

    actual fun encodePng(image: PlatformImage): ByteArray? {
        // K16：CoreGraphics 光栅化 → pngData
        BadgerLog.w(TAG, "encodePng: iOS 骨架未接线（K16）")
        return null
    }

    actual fun scaleToMaxSide(image: PlatformImage, maxSide: Int): PlatformImage {
        // K16：UIGraphicsBeginImageContextWithOptions 重绘
        return image
    }

    actual val DEFAULT_WEBP_QUALITY: Int = 90
    actual val AVATAR_SIZE: Int = 256
    actual val COLLECTION_BG_SIZE: Int = 1080
}
