package top.mcxiafeng.badger.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "CacheFiles.ios"

/** [KMP K13c] iOS actual：NSCachesDirectory/shared。 */
@OptIn(ExperimentalForeignApi::class)
actual object CacheFiles {

    private fun cacheDir(): String {
        val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return dirs.firstOrNull() as? String ?: error("NSCachesDirectory 不可用")
    }

    actual fun writeTextToCache(subDir: String, fileName: String, content: String): String? {
        return try {
            val dir = cacheDir() + "/" + subDir
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            val path = "$dir/$fileName"
            val ok = (content as NSString).writeToFile(path, true)
            if (!ok) {
                BadgerLog.e(TAG, "writeToFile 失败: $fileName")
                return null
            }
            path
        } catch (e: Exception) {
            BadgerLog.e(TAG, "writeTextToCache 失败: $fileName", e)
            null
        }
    }

    actual fun deleteCachedFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { NSFileManager.defaultManager.removeItemAtPath(path, error = null) }
    }
}
