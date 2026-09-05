package top.mcxiafeng.badger.platform

import java.io.File
import java.io.FileOutputStream
import top.mcxiafeng.badger.shared.db.SpikeContextHolder
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageFiles"

/** [KMP K13c] Android actual：filesDir 落盘（路径语义与原 Methods 一致：根目录直存）。 */
actual object ImageFiles {

    private fun dir(): File {
        val context = SpikeContextHolder.appContext
            ?: error("ImageFiles: appContext 未初始化（应在 Application.onCreate 注入）")
        return context.filesDir
    }

    actual fun saveAvatarImage(bytes: ByteArray, fileName: String): String? =
        save(bytes, fileName, "saveAvatarImage")

    actual fun saveCollectionBackground(bytes: ByteArray, fileName: String): String? =
        save(bytes, fileName, "saveCollectionBackground")

    private fun save(bytes: ByteArray, fileName: String, caller: String): String? {
        return try {
            val file = File(dir(), fileName)
            FileOutputStream(file).use { it.write(bytes) }
            BadgerLog.d(TAG, "$caller: $fileName (${bytes.size} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            BadgerLog.e(TAG, "$caller 失败: $fileName", e)
            null
        }
    }

    actual fun loadImageBytes(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            BadgerLog.e(TAG, "loadImageBytes 失败: $path", e)
            null
        }
    }

    actual fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists() && !file.delete()) {
            BadgerLog.w(TAG, "deleteImageFile 删除失败: $path")
        }
    }

    actual fun imageFileExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }
}
