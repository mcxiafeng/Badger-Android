package top.mcxiafeng.badger.data.repository

import top.mcxiafeng.badger.platform.ImageCodec
import top.mcxiafeng.badger.platform.ImageFiles
import top.mcxiafeng.badger.platform.PlatformImage
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.BadgerLog

/** QQ 头像下载超时（原 ContactRepositoryImpl.AVATAR_TIMEOUT_MS，随 avatarFetcher 注入点落 app） */
private const val AVATAR_TIMEOUT_MS = 5_000L

/**
 * [KMP K08-B] avatarFetcher 的 Android 实现：`HttpUtil.downloadBitmap` 下载 QQ 头像 →
 * 缩放到 AVATAR_SIZE → WEBP 编码 → `ImageFiles.saveAvatarImage` 落盘，返回文件绝对路径；
 * null = 下载失败。Bitmap 的回收封装在实现内部（repository 本体无 android.graphics 依赖）。
 *
 * [K3 冒烟修复] K08c 把 ContactRepositoryImpl 迁 commonMain 时拆出该 lambda 注入点，
 * 但 Koin 定义未落——运行期 ContactRepositoryImpl 解析 NoDefinitionFoundException(Function3)
 * 启动即崩（单测用假模块未覆盖真实 Koin 图）。
 *
 * [KMP K13b] 由 app 侧迁 shared androidMain：Methods.saveBitmapAsAvatar →
 * ImageCodec/ImageFiles 边界（语义一致：256px 缩放 + WEBP 60 压缩）。
 */
suspend fun downloadAndSaveAvatar(url: String, uin: Long): String? {
    val bitmap = HttpUtil.downloadBitmap(url, timeoutMs = AVATAR_TIMEOUT_MS) ?: return null
    val image = PlatformImage(bitmap)
    try {
        val scaled = ImageCodec.scaleToMaxSide(image, ImageCodec.AVATAR_SIZE)
        try {
            val bytes = ImageCodec.encodeWebp(scaled, AVATAR_WEBP_QUALITY)
            if (bytes == null) {
                BadgerLog.w("AvatarFetcher", "头像编码失败: $uin")
                return null
            }
            return ImageFiles.saveAvatarImage(bytes, ContactRepositoryImpl.qqAvatarFileName(uin))
        } finally {
            if (scaled !== image) scaled.close()
        }
    } finally {
        image.close()
    }
}

private const val AVATAR_WEBP_QUALITY = 60
