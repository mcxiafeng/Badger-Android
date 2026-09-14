package top.mcxiafeng.badger.platform

expect class PlatformImage {
    val width: Int
    val height: Int

    
    fun close()
}
