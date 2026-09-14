package top.mcxiafeng.badger.platform

expect object ImageCodec {

    
    fun decode(bytes: ByteArray): PlatformImage?

    
    fun encodeWebp(image: PlatformImage, quality: Int = DEFAULT_WEBP_QUALITY): ByteArray?

    
    fun encodePng(image: PlatformImage): ByteArray?

    
    fun scaleToMaxSide(image: PlatformImage, maxSide: Int): PlatformImage

    
    val AVATAR_SIZE: Int

    
    val COLLECTION_BG_SIZE: Int

    val DEFAULT_WEBP_QUALITY: Int
}
