package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.platform.PlatformImage

enum class CropMode {
    BANNER,
    AVATAR,
    COVER,
    COLLECTION_BG
}

data class CropConfig(
    val mode: CropMode = CropMode.BANNER,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 0
)

@Composable
expect fun ImageCropDialog(
    image: PlatformImage,
    cropConfig: CropConfig = CropConfig(),
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
)
