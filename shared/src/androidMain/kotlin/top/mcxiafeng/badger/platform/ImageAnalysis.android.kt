package top.mcxiafeng.badger.platform

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.shared.util.BadgerDispatchers
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageAnalysis"
private const val PALETTE_SWATCH_LIMIT = 8
private const val SAMPLE_STEP = 4

/** [KMP K13c] Android actual：底部 1/3 中心条带亮度采样（逐行对齐原 CollectionTheme 实现）。 */
actual fun textContentColorForImage(
    image: PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color {
    if (image == null) {
        return dominantColor?.let { contentColorFor(it) } ?: fallback
    }
    return try {
        val bitmap = image.bitmap
        if (bitmap.isRecycled) return dominantColor?.let { contentColorFor(it) } ?: fallback
        val w = bitmap.width
        val h = bitmap.height
        // 采样底部 1/3 区域的中心水平条带
        val startY = (h * 2 / 3).coerceAtMost(h - 1)
        var totalLuminance = 0.0
        var sampleCount = 0
        for (y in startY until h step SAMPLE_STEP) {
            for (x in (w / 4) until (w * 3 / 4) step SAMPLE_STEP) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255.0
                val g = (pixel shr 8 and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0
                totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
                sampleCount++
            }
        }
        if (sampleCount == 0) return dominantColor?.let { contentColorFor(it) } ?: fallback
        val avgLuminance = totalLuminance / sampleCount
        // 亮度 > 0.45 用深色文字，否则用白色文字
        if (avgLuminance > 0.45f) Color(0xDE1C1B1FL) else Color.White
    } catch (e: Exception) {
        BadgerLog.w(TAG, "textContentColorForImage 采样失败，使用降级颜色", e)
        dominantColor?.let { contentColorFor(it) } ?: fallback
    }
}

/** [KMP K13c] Android actual：Palette 显色主色（对齐原 ColorExtractor.extractDominantColor）。 */
actual suspend fun extractDominantColor(image: PlatformImage): Long? = withContext(BadgerDispatchers.io) {
    try {
        val bitmap = image.bitmap
        if (bitmap.isRecycled) return@withContext null
        val palette = Palette.from(bitmap).maximumColorCount(PALETTE_SWATCH_LIMIT).generate()
        val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
        swatch?.let { (it.rgb.toLong() and 0xFFFFFFFFL) }
    } catch (e: Exception) {
        BadgerLog.w(TAG, "extractDominantColor 失败", e)
        null
    }
}
