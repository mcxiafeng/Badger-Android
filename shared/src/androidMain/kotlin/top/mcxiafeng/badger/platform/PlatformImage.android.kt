package top.mcxiafeng.badger.platform

import android.graphics.Bitmap

actual class PlatformImage(val bitmap: Bitmap) {
    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height

    actual fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
