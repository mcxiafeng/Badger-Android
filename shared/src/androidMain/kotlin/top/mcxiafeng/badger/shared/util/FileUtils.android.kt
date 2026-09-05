package top.mcxiafeng.badger.shared.util

import java.io.File

/**
 * [KMP K08-B] Android actual：java.io.File 删除。
 */
actual fun deleteFileQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    val file = File(path)
    if (file.exists()) file.delete()
}
