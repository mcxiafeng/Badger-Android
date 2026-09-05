package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIImage

/**
 * [KMP K10] iOS actual：包装 UIImage（CoreImage / Vision 检测输入）。
 *
 * close 为 no-op：iOS 由 ARC 管理内存，无显式回收语义。
 */
@OptIn(ExperimentalForeignApi::class)
actual class PlatformImage(val uiImage: UIImage) {
    /** 像素宽（points × scale，与 Android Bitmap.width 语义对齐） */
    actual val width: Int
        get() = uiImage.size.useContents { (width * uiImage.scale).toInt() }

    actual val height: Int
        get() = uiImage.size.useContents { (height * uiImage.scale).toInt() }

    actual fun close() {
        // ARC 自管理，无操作
    }
}
