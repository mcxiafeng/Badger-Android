package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "ImageFiles.ios"

/**
 * [KMP K13c] iOS actual：NSDocumentDirectory 落盘。
 * 与 Android filesDir 同语义（App 私有目录，卸载即清除）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object ImageFiles {

    private fun documentsDir(): String {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        return docs.firstOrNull() as? String ?: error("NSDocumentDirectory 不可用")
    }

    private fun save(bytes: ByteArray, fileName: String, caller: String): String? {
        return try {
            val path = documentsDir() + "/" + fileName
            val data = bytes.toNSData()
            val ok = data.writeToFile(path, true)
            if (!ok) {
                BadgerLog.e(TAG, "$caller writeToFile 失败: $fileName")
                return null
            }
            BadgerLog.d(TAG, "$caller: $fileName (${bytes.size} bytes)")
            path
        } catch (e: Exception) {
            BadgerLog.e(TAG, "$caller 失败: $fileName", e)
            null
        }
    }

    actual fun saveAvatarImage(bytes: ByteArray, fileName: String): String? =
        save(bytes, fileName, "saveAvatarImage")

    actual fun saveCollectionBackground(bytes: ByteArray, fileName: String): String? =
        save(bytes, fileName, "saveCollectionBackground")

    actual fun loadImageBytes(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        return try {
            val data = NSData.dataWithContentsOfFile(path) ?: return null
            data.toByteArray()
        } catch (e: Exception) {
            BadgerLog.e(TAG, "loadImageBytes 失败: $path", e)
            null
        }
    }

    actual fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, error = null) }
    }

    actual fun imageFileExists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
    return out
}
