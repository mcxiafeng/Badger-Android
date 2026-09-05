package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 头像/背景图下载落盘组合助手（替代原 HttpUtil.downloadBitmap +
 * Methods.saveBitmapAsAvatar 的 UI 侧调用模式）。
 *
 * 语义：下载（[downloadImage]）→ 缩放到 AVATAR_SIZE → WEBP 60 压缩 → 私有目录落盘。
 * 失败返回 null（调用方记日志/Toast）。
 */
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

/**
 * 下载图片并统一编码为 PNG 字节（头像大图预览显示 / 保存原图到相册共用）。
 * 统一 PNG 的原因：GallerySaver 按 image/png 落 MediaStore，展示走 decodeToImageBitmap。
 */
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
