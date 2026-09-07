package top.mcxiafeng.badger.platform

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGDataProviderCopyData
import platform.UIKit.UIGraphicsImageRenderer
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageAnalysis.ios"
private const val LUMINANCE_THRESHOLD = 0.45

/**
 * [KMP K13c→K16] iOS actual：像素采样经 UIGraphicsImageRenderer 1×1 光栅化 +
 * CGDataProviderCopyData 读取原始字节。
 *
 * textContentColorForImage：dominantColor 参数路径（调用方先 extractDominantColor 取色）。
 * extractDominantColor：绘制 1×1 位图取全局平均色（近似 Android Palette dominantSwatch）。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun textContentColorForImage(
    image: PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color {
    if (image != null) {
        BadgerLog.d(TAG, "textContentColorForImage: 走 dominantColor 路径")
    }
    return dominantColor?.let { contentColorFor(it) } ?: fallback
}

/**
 * [KMP K13c→K16] iOS actual：绘制 1×1 位图取全局平均色。
 * 近似 Android Palette dominantSwatch（不逐色聚类，取全图均值——对名片背景主色足够）。
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun extractDominantColor(image: PlatformImage): Long? {
    return try {
        // 绘制 1×1 位图取平均色
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(1.0, 1.0))
        val avgImage = renderer.imageWithActions { _ ->
            image.uiImage.drawInRect(CGRectMake(0.0, 0.0, 1.0, 1.0))
        }

        // 从 1×1 UIImage 读取原始像素数据
        val avgCgImage = avgImage.CGImage ?: return null
        val dataProvider = CGImageGetDataProvider(avgCgImage)
        val cfData = CGDataProviderCopyData(dataProvider) ?: return null

        // CFData → ByteArray（toll-free bridged with NSData）
        val bytes = (cfData as platform.Foundation.NSData).toByteArray()
        if (bytes.size < 4) return null

        // UIGraphicsImageRenderer 默认 sRGB premultiplied-first (ARGB)
        val a = bytes[0].toLong() and 0xFF
        val r = bytes[1].toLong() and 0xFF
        val g = bytes[2].toLong() and 0xFF
        val b = bytes[3].toLong() and 0xFF

        // 返回 ARGB Long（对齐 Android Color.toArgb().toLong() and 0xFFFFFFFFL）
        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
        BadgerLog.d(TAG, "extractDominantColor: argb=0x${argb.toString(16)}")
        argb
    } catch (e: Exception) {
        BadgerLog.w(TAG, "extractDominantColor 采样失败", e)
        null
    }
}
