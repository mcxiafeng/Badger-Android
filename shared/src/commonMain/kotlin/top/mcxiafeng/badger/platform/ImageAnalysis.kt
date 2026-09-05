package top.mcxiafeng.badger.platform

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

/**
 * [KMP K13c] 图像像素分析边界（名片夹背景图上的文字对比度自适应）。
 *
 * 纯函数部分（isLightColor/contentColorFor/subTextColorFor）随边界下沉 common
 * （原 app/ui/components/CollectionTheme.kt 中的同名函数在 K13c UI 迁移时收敛为委托）；
 * 仅依赖像素采样的 [textContentColorForImage] 走平台边界。
 *
 * Android actual = Bitmap.getPixel 底部 1/3 中心条带采样（逐行对齐原实现）；
 * iOS actual = 骨架（直接走 dominantColor 兜底，K16 接 CGImage 采样）。
 */

/** 亮度 > 0.5 视为浅色（对齐原 CollectionTheme.isLightColor）。 */
fun isLightColor(color: Long): Boolean {
    val c = Color(color)
    val luminance = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
    return luminance > 0.5
}

/** 主题色 → 内容文字色（浅色底用深字，深色底用白字）。 */
fun contentColorFor(themeColor: Long?): Color {
    if (themeColor == null || themeColor == 0L) return Color.Unspecified
    return if (isLightColor(themeColor)) Color(0xFF1C1B1FL) else Color.White
}

/**
 * [KMP K13c] 主色提取边界（原 app utils/ColorExtractor 的 Palette 半边）。
 * Android actual = Palette.Swatch 最显色主色；iOS actual = 骨架（K16 平均色）。
 * 失败返回 null（调用方走 dominantColor 兜底）。
 */
expect suspend fun extractDominantColor(image: PlatformImage): Long?

/** 从文件路径加载解码图像（IO 调度器；失败返回 null）。 */
suspend fun loadDecodedImage(path: String?): PlatformImage? = withContext(BadgerDispatchers.io) {
    val bytes = ImageFiles.loadImageBytes(path) ?: return@withContext null
    ImageCodec.decode(bytes)
}
expect fun textContentColorForImage(
    image: PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color
