package top.mcxiafeng.badger.platform

expect object GallerySaver {
    
    fun saveImagePng(bytes: ByteArray, displayName: String): Boolean
}
