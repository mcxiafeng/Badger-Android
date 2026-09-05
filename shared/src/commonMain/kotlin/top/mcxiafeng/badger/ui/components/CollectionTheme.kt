package top.mcxiafeng.badger.ui.components

import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.platform.textContentColorForImage
import top.mcxiafeng.badger.utils.BadgerLog

/**
 * 名片夹主题色工具（[KMP K13c] 迁 commonMain）。
 *
 * 纯函数（isLightColor/contentColorFor/subTextColorFor）下沉到
 * `platform/ImageAnalysis.kt` 后由本文件 re-export 兼容旧 import 路径；
 * 像素采样版 [textContentColorForImage] 走平台边界（原 textContentColorForBitmap 的 Bitmap
 * 参数改为 PlatformImage——调用方经 ImageCodec.decode 从文件字节构造）。
 */

/** 副文字颜色：主文字为白色时用 80% 白，主文字为深色时用 onSurfaceVariantSummary */
fun subTextColorFor(primaryTextColor: Color, darkFallback: Color): Color {
    return if (primaryTextColor == Color.White) {
        Color.White.copy(alpha = 0.8f)
    } else {
        darkFallback
    }
}

/** 背景图像素 → 文字颜色（委托平台边界；dominantColor 为解析失败的降级依据）。 */
fun collectionTextContentColor(
    image: top.mcxiafeng.badger.platform.PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color = textContentColorForImage(image, dominantColor, fallback)
