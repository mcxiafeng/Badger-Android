package top.mcxiafeng.badger.platform

import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageAnalysis.ios"

/**
 * [KMP K13c] iOS actual 骨架：像素采样未接（K16 走 CGImage CGContext 光栅化采样）。
 * 当前直接走 dominantColor 兜底——与原实现的 bitmap==null 分支语义一致，可读性优先。
 */
actual fun textContentColorForImage(
    image: PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color {
    if (image != null) {
        BadgerLog.d(TAG, "iOS 像素采样骨架：走 dominantColor 兜底（K16 接 CGImage）")
    }
    return dominantColor?.let { contentColorFor(it) } ?: fallback
}

/** [KMP K13c] iOS actual 骨架：K16 接平均色采样。 */
actual suspend fun extractDominantColor(image: PlatformImage): Long? {
    BadgerLog.d(TAG, "extractDominantColor: iOS 骨架（K16）")
    return null
}
