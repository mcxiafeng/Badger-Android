package top.mcxiafeng.badger.platform

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.withContext
import top.mcxiafeng.badger.shared.util.BadgerDispatchers

fun isLightColor(color: Long): Boolean {
    val c = Color(color)
    val luminance = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
    return luminance > 0.5
}

fun contentColorFor(themeColor: Long?): Color {
    if (themeColor == null || themeColor == 0L) return Color.Unspecified
    return if (isLightColor(themeColor)) Color(0xFF1C1B1FL) else Color.White
}

expect suspend fun extractDominantColor(image: PlatformImage): Long?

suspend fun loadDecodedImage(path: String?): PlatformImage? = withContext(BadgerDispatchers.io) {
    val bytes = ImageFiles.loadImageBytes(path) ?: return@withContext null
    ImageCodec.decode(bytes)
}
expect fun textContentColorForImage(
    image: PlatformImage?,
    dominantColor: Long?,
    fallback: Color,
): Color
