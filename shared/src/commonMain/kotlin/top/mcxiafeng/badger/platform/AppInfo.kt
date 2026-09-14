package top.mcxiafeng.badger.platform

expect suspend fun downloadImage(
    url: String,
    timeoutMs: Long = DEFAULT_IMAGE_TIMEOUT_MS,
    headers: Map<String, String> = emptyMap(),
): PlatformImage?

const val DEFAULT_IMAGE_TIMEOUT_MS = 5_000L

interface AppInfo {
    val versionName: String
    val versionCode: Int
    val buildDate: String
}
