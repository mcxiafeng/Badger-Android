package top.mcxiafeng.badger.platform

expect object CacheFiles {

    
    fun writeTextToCache(subDir: String, fileName: String, content: String): String?

    
    fun deleteCachedFile(path: String?)
}
