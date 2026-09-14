package top.mcxiafeng.badger.platform

suspend fun downloadAndStoreAvatar(
    url: String,
    fileName: String,
    headers: Map<String, String> = emptyMap(),
): String? {
    val image = downloadImage(url, headers = headers) ?: return null
    return try {
        val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
        try {
            val bytes = ImageCodec.encodeWebp(scaled, AVATAR_SAVE_QUALITY) ?: return null
            ImageFiles.saveAvatarImage(bytes, fileName)
        } finally {
            if (scaled !== image) scaled.close()
        }
    } finally {
        image.close()
    }
}

private const val AVATAR_SAVE_QUALITY = 60

suspend fun downloadImageAsPng(
    url: String,
    timeoutMs: Long = DEFAULT_IMAGE_TIMEOUT_MS,
    headers: Map<String, String> = emptyMap(),
): ByteArray? {
    val image = downloadImage(url, timeoutMs = timeoutMs, headers = headers) ?: return null
    return try {
        ImageCodec.encodePng(image)
    } finally {
        image.close()
    }
}
