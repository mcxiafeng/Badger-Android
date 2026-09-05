package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 缓存文件写边界（分享前落盘用；iOS 无 kotlin.io.File）。
 * Android actual = cacheDir/shared；iOS actual = NSCachesDirectory。
 */
expect object CacheFiles {

    /** 把文本写入缓存目录并返回绝对路径；失败返回 null。 */
    fun writeTextToCache(subDir: String, fileName: String, content: String): String?

    /** 删除缓存文件（静默）。 */
    fun deleteCachedFile(path: String?)
}
