package top.mcxiafeng.badger.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * [KMP K08-B] iOS actual：NSFileManager 删除。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun deleteFileQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}
