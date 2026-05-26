package top.mcxiafeng.badger.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

fun isLightColor(color: Long): Boolean {
    val c = Color(color)
    val luminance = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
    return luminance > 0.5
}

fun contentColorFor(themeColor: Long?): Color {
    if (themeColor == null || themeColor == 0L) return Color.Unspecified
    return if (isLightColor(themeColor)) Color(0xFF1C1B1FL) else Color.White
}

/**
 * 根据背景图底部区域的实际像素亮度计算文字颜色。
 * 采样底部 1/3 区域的中心条带，保证文字所在区域的对比度。
 */
fun textContentColorForBitmap(bitmap: Bitmap?, dominantColor: Long?, fallback: Color): Color {
    if (bitmap == null || bitmap.isRecycled) {
        return dominantColor?.let { contentColorFor(it) } ?: fallback
    }
    try {
        val w = bitmap.width
        val h = bitmap.height
        // 采样底部 1/3 区域的中心水平条带
        val startY = (h * 2 / 3).coerceAtMost(h - 1)
        val endY = h
        val sampleStep = 4 // 每4个像素采样一次以节省性能
        var totalLuminance = 0.0
        var sampleCount = 0
        for (y in startY until endY step sampleStep) {
            for (x in (w / 4) until (w * 3 / 4) step sampleStep) {
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
        return if (avgLuminance > 0.45f) Color(0xDE1C1B1FL) else Color.White
    } catch (_: Exception) {
        return dominantColor?.let { contentColorFor(it) } ?: fallback
    }
}

/**
 * 副文字颜色：主文字为白色时用 80% 白，主文字为深色时用 onSurfaceVariantSummary
 */
fun subTextColorFor(primaryTextColor: Color, darkFallback: Color): Color {
    return if (primaryTextColor == Color.White) {
        Color.White.copy(alpha = 0.8f)
    } else {
        darkFallback
    }
}
