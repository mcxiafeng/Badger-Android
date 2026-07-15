package top.mcxiafeng.badger.utils

import android.graphics.Bitmap
import android.util.Log
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.network.PlatformAdapterRegistry
import androidx.core.graphics.scale

private const val TAG = "Tester"

data class ExtractedStyle(
    val themeColor: Long,
    val titleTextColor: Long,
    val bodyTextColor: Long
)

suspend fun extractDominantColor(bitmap: Bitmap): ExtractedStyle? {
    return withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val scaled = if (bitmap.width > 200) {
                val ratio = 200f / bitmap.width
                bitmap.scale(200, (bitmap.height * ratio).toInt())
            } else {
                bitmap
            }
            val palette = Palette.from(scaled)
                .maximumColorCount(8)
                .generate()
            // 缩放产生的中间 Bitmap 必须回收，Palette 已提取完数据不再需要
            if (scaled !== bitmap) scaled.recycle()
            val swatch = palette.dominantSwatch
            if (swatch == null) {
                Log.d(TAG, "extractDominantColor: no dominant swatch found")
                return@withContext null
            }
            val elapsed = System.currentTimeMillis() - startTime
            val style = ExtractedStyle(
                themeColor = (swatch.rgb.toLong() and 0xFFFFFFFFL).also {
                    if (it == 0L) Log.w(TAG, "extractDominantColor: dominant swatch rgb is 0 (transparent)")
                },
                titleTextColor = swatch.titleTextColor.toLong() and 0xFFFFFFFFL,
                bodyTextColor = swatch.bodyTextColor.toLong() and 0xFFFFFFFFL
            )
            // 0L 是全透明色，不可作为主题色
            if (style.themeColor == 0L) {
                Log.d(TAG, "extractDominantColor: themeColor is 0L, returning null")
                return@withContext null
            }
            Log.d(TAG, "extractDominantColor: themeColor=${style.themeColor}, elapsed=${elapsed}ms")
            style
        } catch (e: Exception) {
            Log.d(TAG, "extractDominantColor: error: ${e.message}")
            null
        }
    }
}

suspend fun getPlatformBrandColor(qrContent: String): Long? {
    return try {
        val type = PlatformAdapterRegistry.resolveContentType(qrContent).first
        val tagInfo = PlatformAdapterRegistry.getTagInfo(type)
        if (tagInfo != null) {
            val color = tagInfo.color and 0xFFFFFFFFL
            Log.d(TAG, "getPlatformBrandColor: type=$type, color=$color")
            color
        } else {
            Log.d(TAG, "getPlatformBrandColor: no tagInfo for type=$type")
            null
        }
    } catch (e: Exception) {
        Log.d(TAG, "getPlatformBrandColor: error: ${e.message}")
        null
    }
}
