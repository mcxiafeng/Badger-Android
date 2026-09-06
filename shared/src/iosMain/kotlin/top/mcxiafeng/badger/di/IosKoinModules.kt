package top.mcxiafeng.badger.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import top.mcxiafeng.badger.data.AppDatabase
import top.mcxiafeng.badger.data.prefs.AuthPrefs
import top.mcxiafeng.badger.data.repository.ServerApiFactory
import top.mcxiafeng.badger.data.repository.downloadAndSaveAvatar
import top.mcxiafeng.badger.network.KtorServerApi
import top.mcxiafeng.badger.network.ServerApi
import top.mcxiafeng.badger.network.TokenHolder
import top.mcxiafeng.badger.platform.AppInfo
import top.mcxiafeng.badger.platform.IosAppLinkHandler
import top.mcxiafeng.badger.shared.db.iosAppDatabaseBuilder
import top.mcxiafeng.badger.sync.SyncDispatcher
import top.mcxiafeng.badger.sync.SyncEngine

/**
 * [KMP K16] iOS 平台 Koin 模块（与 app 侧 KoinModules 对位的平台差异面）。
 *
 * 装载顺序（IosAppBootstrap）：iosDatabaseModule → iosRepositoryModule → iosNetworkModule →
 * useCaseModule → iosAppStateModule → viewModelModule。
 */

/** iOS AppDatabase：NSDocumentDirectory + bundled driver + seed（Q4：全新库 + pull bootstrap）。 */
val iosDatabaseModule = module {
    includes(daoModule)
    single<AppDatabase> { iosAppDatabaseBuilder().build() }
}

/** iOS 仓库层：avatarFetcher = Ktor 下载 + UIImage 编码落盘（AvatarFetcher.ios.kt）。 */
val iosRepositoryModule = commonRepositoryModule(::downloadAndSaveAvatar)

/** iOS 网络层：KtorServerApi（Darwin 引擎 + 401 刷新钩子）。 */
val iosNetworkModule = module {
    single { TokenHolder() }
    single { ServerApiFactory() }
    single<ServerApi> {
        val initialUrl = AuthPrefs.readServerUrl()
        KtorServerApi(
            baseUrl = initialUrl,
            tokenHolder = get(),
            outboxStore = get(),
            kickScheduler = { KoinComponentBy.get<SyncDispatcher>().kick() },
        ).also { api ->
            get<ServerApiFactory>().install(api, initialUrl)
        }
    }
}

/** iOS App 状态层：公共部分 + 平台差异（SyncDispatcher / AppInfo / AppLinkHandler）。 */
val iosAppStateModule = module {
    includes(commonAppStateModule)
    // [KMP K09/K16] Outbox 重放调度：BGAppRefreshTask + 前台兜底（替代 Android WorkManager）
    single { SyncDispatcher(replay = { KoinComponentBy.get<SyncEngine>().pushOnce(false) }) }
    single<AppInfo> { IosAppInfo() }
    // [KMP K13c] iOS 侧 deep link：universal link 接线留 iosApp 壳（当前无事件源）
    single<top.mcxiafeng.badger.platform.AppLinkHandler> { IosAppLinkHandler() }
}

/**
 * iOS Coil ImageLoader（ktor3 fetcher）。经 MainViewController 的
 * `setSingletonImageLoaderFactory` 注入（PlatformContext 由 Coil 提供）。
 * 内存预算对齐 Android 25% 档（中端 iOS 设备 ~128MB）；磁盘缓存 NSCachesDirectory。
 */
fun iosImageLoader(context: PlatformContext): ImageLoader {
    val cacheDir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: NSTemporaryDirectory()
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizeBytes(MEMORY_CACHE_BYTES)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory("$cacheDir/image_cache".toPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .components { add(KtorNetworkFetcherFactory()) }
        .crossfade(true)
        .build()
}

private const val MEMORY_CACHE_BYTES = 128L * 1024 * 1024
private const val DISK_CACHE_BYTES = 64L * 1024 * 1024

/** [KMP K16] AppInfo 的 iOS 实现（Bundle 版本信息；构建日期由 iosApp Info.plist 注入）。 */
class IosAppInfo : AppInfo {
    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    override val versionCode: Int =
        ((NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String) ?: "0").toIntOrNull() ?: 0

    override val buildDate: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("BadgerBuildDate") as? String) ?: "iOS"
}
