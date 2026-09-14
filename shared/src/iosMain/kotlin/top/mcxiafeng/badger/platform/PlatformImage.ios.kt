package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
actual class PlatformImage(val uiImage: UIImage) {
    
    actual val width: Int
        get() = uiImage.size.useContents { (width * uiImage.scale).toInt() }

    actual val height: Int
        get() = uiImage.size.useContents { (height * uiImage.scale).toInt() }

    actual fun close() {
        
    }
}
