package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 应用私有目录图像文件存取边界（替代 Methods 的 Bitmap 落盘半边）。
 *
 * 路径语义与原 Methods 实现一致：直接落在 Android filesDir 根目录（无子目录），
 * 文件名由调用方生成（contact_{id}_avatar.webp / user_avatar.webp / collection_bg_*）。
 *
 * iOS actual = NSDocumentDirectory + NSData（K16）。
 */
expect object ImageFiles {

    /** 保存头像字节（原语义：缩放 AVATAR_SIZE 后 WEBP 压缩由调用方编码侧完成，这里只落盘）。 */
    fun saveAvatarImage(bytes: ByteArray, fileName: String): String?

    /** 保存名片夹背景字节。 */
    fun saveCollectionBackground(bytes: ByteArray, fileName: String): String?

    /** 读取图像文件字节；路径空/不存在返回 null。 */
    fun loadImageBytes(path: String?): ByteArray?

    /** 删除图像文件（路径空/不存在静默）。 */
    fun deleteImageFile(path: String?)

    /** 文件存在且非空（SetupStepProfile 头像竞态防护用）。 */
    fun imageFileExists(path: String?): Boolean

    /** 按 [saveAvatarImage] 同源私有目录按"文件名"判断存在性（相对文件名直传 [imageFileExists] 会解析到进程 CWD 恒 false）。 */
    fun avatarFileExists(fileName: String): Boolean

    /** 本地文件最后修改时间（毫秒，缺失/异常返回 0）——头像同名覆盖后的渲染缓存键失效依据。 */
    fun imageFileLastModified(path: String?): Long
}
