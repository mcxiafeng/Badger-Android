package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 图像编解码边界（common 侧唯一的 Bitmap/UIImage 进出口）。
 *
 * Android actual = BitmapFactory / Bitmap.compress（WEBP 与原 Methods 压缩语义一致）；
 * iOS actual = UIImage jpeg/png 编码（WebP 无系统解码器，降级 JPEG，K16 文件名统一）。
 *
 * 注意：decode 返回的 PlatformImage 由调用方持有并在生命周期终点 [PlatformImage.close]
 * （Android=recycle，对齐 AGENTS.md Bitmap 内存纪律）。
 */
expect object ImageCodec {

    /** 字节流解码为平台图像；失败返回 null（调用方记日志降级）。 */
    fun decode(bytes: ByteArray): PlatformImage?

    /** 编码为 WEBP 字节（quality 0-100）。 */
    fun encodeWebp(image: PlatformImage, quality: Int = DEFAULT_WEBP_QUALITY): ByteArray?

    /** 编码为 PNG 字节（无损；相册保存/QR 落盘用）。 */
    fun encodePng(image: PlatformImage): ByteArray?

    /** 等比缩放到最长边 = maxSide（小于 maxSide 时原样返回，语义对齐 Methods.scaleBitmap）。 */
    fun scaleToMaxSide(image: PlatformImage, maxSide: Int): PlatformImage

    /** 头像落盘缩放目标（px，对齐 Methods.AVATAR_SIZE）。 */
    val AVATAR_SIZE: Int

    /** 名片夹背景图缩放目标（px，对齐 Methods.COLLECTION_BG_SIZE）。 */
    val COLLECTION_BG_SIZE: Int

    val DEFAULT_WEBP_QUALITY: Int
}
