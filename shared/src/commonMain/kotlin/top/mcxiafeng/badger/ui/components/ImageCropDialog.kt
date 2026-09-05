package top.mcxiafeng.badger.ui.components

import androidx.compose.runtime.Composable
import top.mcxiafeng.badger.platform.PlatformImage

/**
 * [KMP K13c] 裁剪模式（原 app 实现的类型上收，纯数据）。
 */
enum class CropMode {
    BANNER,
    AVATAR,
    COVER,
    COLLECTION_BG
}

/**
 * 裁剪输出配置。
 */
data class CropConfig(
    val mode: CropMode = CropMode.BANNER,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 0
)

/**
 * [KMP K13c] 图片裁剪对话框平台边界。
 *
 * 原实现为 Compose 自绘 + Bitmap 像素操作（拖动/缩放手势、单一真理坐标系），Android actual
 * 原样保留该实现；输入从 `Uri` 改为调用方预解码的 [PlatformImage]，输出从 `Bitmap`
 * 改为 WEBP 编码字节（调用方直接落盘 [top.mcxiafeng.badger.platform.ImageFiles]）。
 *
 * iOS actual：骨架对话框（K16 换像素访问层后可整段上收 common）。
 */
@Composable
expect fun ImageCropDialog(
    image: PlatformImage,
    cropConfig: CropConfig = CropConfig(),
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
)
