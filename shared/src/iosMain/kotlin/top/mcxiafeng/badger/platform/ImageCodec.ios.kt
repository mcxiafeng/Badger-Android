package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageCodec.ios"

/**
 * [KMP K16] iOS actual 实接：
 * - decode：UIImage(data:)；
 * - encodeWebp：iOS 无系统 WebP 编码器 → **降级 JPEG 字节**（落盘文件名不变；
 *   Coil/BitmapFactory 均按内容嗅探解码，扩展名不影响双端读取）；
 * - encodePng：UIImagePNGRepresentation；
 * - scaleToMaxSide：UIGraphics 重绘（像素空间，保持 UIImage.scale）。
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
        // iOS 无 WebP 系统编解码器：JPEG 降级（quality 0-100 → compressionQuality 0-1）
        val data = UIImageJPEGRepresentation(image.uiImage, quality.coerceIn(0, 100) / 100.0)
        if (data == null) {
            BadgerLog.e(TAG, "encodeWebp(JPEG 降级) 失败", null)
            return null
        }
        return data.toByteArray()
    }

    actual fun encodePng(image: PlatformImage): ByteArray? {
        val data = UIImagePNGRepresentation(image.uiImage)
        if (data == null) {
            BadgerLog.e(TAG, "encodePng 失败", null)
            return null
        }
        return data.toByteArray()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun scaleToMaxSide(image: PlatformImage, maxSide: Int): PlatformImage {
        val ui = image.uiImage
        val pixelW = ui.size.useContents { width * ui.scale }
        val pixelH = ui.size.useContents { height * ui.scale }
        if (pixelW <= maxSide && pixelH <= maxSide) return image

        val ratio = minOf(maxSide / pixelW, maxSide / pixelH)
        val pointW = pixelW * ratio / ui.scale
        val pointH = pixelH * ratio / ui.scale
        try {
            // 上下文尺寸是 point 空间（像素 = point × scale），scale 传 ui.scale 保持位图密度
            UIGraphicsBeginImageContextWithOptions(CGSizeMake(pointW, pointH), false, ui.scale)
            ui.drawInRect(CGRectMake(0.0, 0.0, pointW, pointH))
            val scaled = UIGraphicsGetImageFromCurrentImageContext()
                ?: run {
                    BadgerLog.e(TAG, "scaleToMaxSide: context image null，原样返回", null)
                    return image
                }
            return PlatformImage(scaled)
        } catch (e: Exception) {
            BadgerLog.e(TAG, "scaleToMaxSide 失败，原样返回", e)
            return image
        } finally {
            UIGraphicsEndImageContext()
        }
    }

    actual val DEFAULT_WEBP_QUALITY: Int = 90
    actual val AVATAR_SIZE: Int = 256
    actual val COLLECTION_BG_SIZE: Int = 1080
}
