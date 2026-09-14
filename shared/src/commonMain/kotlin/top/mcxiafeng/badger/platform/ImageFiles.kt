package top.mcxiafeng.badger.platform

expect object ImageFiles {

    
    fun saveAvatarImage(bytes: ByteArray, fileName: String): String?

    
    fun saveCollectionBackground(bytes: ByteArray, fileName: String): String?

    
    fun loadImageBytes(path: String?): ByteArray?

    
    fun deleteImageFile(path: String?)

    
    fun imageFileExists(path: String?): Boolean

    
    fun avatarFileExists(fileName: String): Boolean

    
    fun imageFileLastModified(path: String?): Long
}
