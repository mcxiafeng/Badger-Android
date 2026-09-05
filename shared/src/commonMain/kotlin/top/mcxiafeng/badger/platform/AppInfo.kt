package top.mcxiafeng.badger.platform

/**
 * [KMP K13c] 网络图片下载边界（联系人详情头像预览等 UI 路径）。
 *
 * Android actual = HttpUtil.downloadBitmap（OkHttp，headers/超时语义一致）；
 * iOS actual = KtorHttpCore GET + ImageCodec.decode（K16 接线，当前返回 null）。
 */
expect suspend fun downloadImage(
    url: String,
    timeoutMs: Long = DEFAULT_IMAGE_TIMEOUT_MS,
    headers: Map<String, String> = emptyMap(),
): PlatformImage?

/** 默认图片下载超时（对齐原 ContactDetailAvatar 的 5s 短超时语义）。 */
const val DEFAULT_IMAGE_TIMEOUT_MS = 5_000L

/**
 * [KMP K13c] 应用版本信息边界（AboutPage 消费）。
 * Android actual 由 app 壳层注入（BuildConfigBUILD_DATE / packageInfo versionName），
 * iOS actual 由 K16 注入（CFBundleShortVersionString）。
 */
interface AppInfo {
    val versionName: String
    val versionCode: Int
    val buildDate: String
}
