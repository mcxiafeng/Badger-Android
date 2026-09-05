package top.mcxiafeng.badger.data.repository

import android.content.Context
import top.mcxiafeng.badger.di.KoinComponentBy
import top.mcxiafeng.badger.utils.HttpUtil
import top.mcxiafeng.badger.utils.Methods

/** QQ 头像下载超时（原 ContactRepositoryImpl.AVATAR_TIMEOUT_MS，随 avatarFetcher 注入点落 app） */
private const val AVATAR_TIMEOUT_MS = 5_000L

/**
 * [KMP K08-B] avatarFetcher 的 Android 实现：`HttpUtil.downloadBitmap` 下载 QQ 头像 →
 * `Methods.saveBitmapAsAvatar` 落盘，返回保存后的文件绝对路径；null = 下载失败。
 * Bitmap 的回收封装在实现内部（repository 本体无 android.graphics 依赖）。
 *
 * [K3 冒烟修复] K08c 把 ContactRepositoryImpl 迁 commonMain 时拆出该 lambda 注入点，
 * 但 Koin 定义未落——运行期 ContactRepositoryImpl 解析 NoDefinitionFoundException(Function3)
 * 启动即崩（单测用假模块未覆盖真实 Koin 图）。
 */
internal suspend fun downloadAndSaveAvatar(url: String, uin: Long): String? {
    val bitmap = HttpUtil.downloadBitmap(url, timeoutMs = AVATAR_TIMEOUT_MS) ?: return null
    try {
        return Methods.saveBitmapAsAvatar(
            KoinComponentBy.get<Context>(),
            bitmap,
            ContactRepositoryImpl.qqAvatarFileName(uin)
        ).absolutePath
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}
