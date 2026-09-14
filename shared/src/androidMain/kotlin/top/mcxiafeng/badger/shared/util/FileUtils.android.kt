package top.mcxiafeng.badger.shared.util

import java.io.File

actual fun deleteFileQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    val file = File(path)
    if (file.exists()) file.delete()
}
