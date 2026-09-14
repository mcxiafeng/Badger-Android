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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun extractDominantColor(image: PlatformImage): Long? {
    return try {
        
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(1.0, 1.0))
        val avgImage = renderer.imageWithActions { _ ->
            image.uiImage.drawInRect(CGRectMake(0.0, 0.0, 1.0, 1.0))
        }

        
        val avgCgImage = avgImage.CGImage ?: return null
        val dataProvider = CGImageGetDataProvider(avgCgImage)
        val cfData = CGDataProviderCopyData(dataProvider) ?: return null

        
        val bytes = (cfData as platform.Foundation.NSData).toByteArray()
        if (bytes.size < 4) return null

        
        val a = bytes[0].toLong() and 0xFF
        val r = bytes[1].toLong() and 0xFF
        val g = bytes[2].toLong() and 0xFF
        val b = bytes[3].toLong() and 0xFF

        
        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
        BadgerLog.d(TAG, "extractDominantColor: argb=0x${argb.toString(16)}")
        argb
    } catch (e: Exception) {
        BadgerLog.w(TAG, "extractDominantColor 采样失败", e)
        null
    }
}
