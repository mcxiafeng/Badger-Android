package top.mcxiafeng.badger.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFileQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
