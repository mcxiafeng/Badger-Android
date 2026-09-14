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

val iosDatabaseModule = module {
    includes(daoModule)
    single<AppDatabase> { iosAppDatabaseBuilder().build() }
}

val iosRepositoryModule = commonRepositoryModule(::downloadAndSaveAvatar)

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

val iosAppStateModule = module {
    includes(commonAppStateModule)
    
    single { SyncDispatcher(replay = { KoinComponentBy.get<SyncEngine>().pushOnce(false) }) }
    single<AppInfo> { IosAppInfo() }
    
    single<top.mcxiafeng.badger.platform.AppLinkHandler> { IosAppLinkHandler() }
}

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

class IosAppInfo : AppInfo {
    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    override val versionCode: Int =
        ((NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String) ?: "0").toIntOrNull() ?: 0

    override val buildDate: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("BadgerBuildDate") as? String) ?: "iOS"
}
