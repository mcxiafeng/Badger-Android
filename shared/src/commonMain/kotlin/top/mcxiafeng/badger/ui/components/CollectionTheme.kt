package top.mcxiafeng.badger.ui.components

import androidx.compose.ui.graphics.Color
import top.mcxiafeng.badger.platform.textContentColorForImage
import top.mcxiafeng.badger.utils.BadgerLog

fun subTextColorFor(primaryTextColor: Color, darkFallback: Color): Color {
    return if (primaryTextColor == Color.White) {
        Color.White.copy(alpha = 0.8f)
    } else {
        darkFallback
    }
}

fun collectionTextContentColor(
    image: top.mcxiafeng.badger.platform.PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color = textContentColorForImage(image, dominantColor, fallback)
