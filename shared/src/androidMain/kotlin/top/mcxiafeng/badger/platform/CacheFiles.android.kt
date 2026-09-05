package top.mcxiafeng.badger.platform

import java.io.File
import top.mcxiafeng.badger.utils.BadgerLog

private const val TAG = "CacheFiles.android"

/** [KMP K13c] Android actual：cacheDir/shared（与原 CardPage/LogViewer 落点一致）。 */
actual object CacheFiles {

    actual fun writeTextToCache(subDir: String, fileName: String, content: String): String? {
        return try {
            val dir = File(LogCollector.cacheDirPath(), subDir).apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            BadgerLog.d(TAG, "writeTextToCache: ${file.absolutePath} (${content.length} chars)")
            file.absolutePath
        } catch (e: Exception) {
            BadgerLog.e(TAG, "writeTextToCache 失败: $fileName", e)
            null
        }
    }

    actual fun deleteCachedFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
