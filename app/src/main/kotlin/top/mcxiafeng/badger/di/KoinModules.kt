package top.mcxiafeng.badger.di
import top.mcxiafeng.badger.data.AppDatabaseHost
import top.mcxiafeng.badger.data.repository.downloadAndSaveAvatar
import top.mcxiafeng.badger.DeepLinkBus
import top.mcxiafeng.badger.network.ContactNetworkResolver
import top.mcxiafeng.badger.network.ShortLinkService
import top.mcxiafeng.badger.sync.OutboxScheduler
import top.mcxiafeng.badger.sync.OutboxStore
import top.mcxiafeng.badger.data.repository.ServerApiFactory

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import top.mcxiafeng.badger.network.ServerApi

/**
 * [§14.2] Koin 模块总览（Android 平台壳）。
 *
 * [KMP K16] 双端共用部分上移 shared commonMain（di/CommonKoinModules：useCaseModule /
 * viewModelModule / daoModule / commonRepositoryModule / commonAppStateModule）——FQN 不变，
 * [BadgerApplication] 的 import 零改动。本文件只保留 Android 平台差异：
 * - databaseModule：AppDatabaseHost.build（filesDir builder + destructive 备份）+ DAO 单例（include daoModule）
 * - repositoryModule：avatarFetcher = HttpUtil/Bitmap 实现注入
 * - networkModule：OkHttpClient + token 刷新拦截器 + OkHttpServerApi
 * - imageModule：Coil + OkHttp fetcher
 * - appStateModule：include 公共 + OutboxScheduler（WorkManager）+ DeepLinkBus 绑定
 */

val databaseModule = module {
    includes(daoModule)

    single {
        // [§14.2] [KMP K07] Room KMP：bundled driver，同一 getDatabasePath 文件，迁移链原样
        AppDatabaseHost.build(get())
    }
}

/**
 * 仓库 / 单例绑定 — 平台差异只有 avatarFetcher（HttpUtil.downloadBitmap + Bitmap 落盘，
 * 见 shared androidMain AvatarFetcher.android.kt）。
 */
val repositoryModule = commonRepositoryModule(::downloadAndSaveAvatar)

/**
 * 网络层 — 对应原 Hilt `NetworkModule` + `AuthModule`。
 *
 * 关键保留:[ServerApiFactory] 持有 Volatile ServerApi 引用,需在 NetworkModule.provideOkHttpClient
 * 阶段完成 `factory.install(api, initialUrl)`。Koin 模式下由 [NetworkModule]
 * 自行 install,与 Koin 解耦;此 module 仅负责 `factory { ServerApiFactory() }` 与 `OkHttpClient`。
 *
 * ServerApi 实例本身**也**通过 factory 提供,因为 `factory.get()` 是延迟求值,koin 解析时
 * 如果 factory 还没装入,使用方可在第一次 get().error() 处被识别。
 */
val networkModule = module {
    single { ServerApiFactory() }
    single { NetworkModule.provideTokenHolder() }
    // [迁移] OkHttpClient 注入 token 拦截器由 NetworkModule 统一构造（2 参签名）
    single {
        NetworkModule.provideOkHttpClient(
            context = androidContext(),
            tokenHolder = get(),
        )
    }
    // [KMP K06] HttpUtil（shared androidMain）不依赖 Koin，启动时注入 client 提供器
    single {
        top.mcxiafeng.badger.utils.HttpUtil.clientProvider = { get<okhttp3.OkHttpClient>() }
        true
    }
    // [迁移] ServerApi 构造成功后才 install 进 factory；同时持有 Outbox store/scheduler
    single<ServerApi> {
        NetworkModule.provideServerApi(
            context = androidContext(),
            http = get(),
            tokenHolder = get(),
            outboxStore = get(),
            outboxScheduler = get(),
            factory = get(),
        )
    }
    // [迁移] resolver / 短链服务改为 Koin 单例（构造器注入 ServerApi）
    singleOf(::ContactNetworkResolver)
    singleOf(::ShortLinkService)
}

/**
 * ImageLoader — 与原 Hilt `ImageModule.provideImageLoader` 行为一致。
 */
val imageModule = module {
    single<ImageLoader> {
        val context = androidContext()
        val okHttpClient: okhttp3.OkHttpClient = get()
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .crossfade(true)
            .build()
    }
}

/**
 * Application 单例 — 公共部分在 commonAppStateModule（shared）；Android 侧补：
 * Outbox 重放的 WorkManager 调度器 + Deep link 消费边界绑定。
 */
val appStateModule = module {
    includes(commonAppStateModule)
    // [Phase 2] Outbox 重放的 WorkManager 调度器
    singleOf(::OutboxScheduler)
    // [KMP K11] NFC 写卡平台边界（Android actual = 原 NfcHelper；SocialViewModel/SocialPage 共用单例）
    single { top.mcxiafeng.badger.platform.NfcWriter() }
    // [KMP K16] AppInfo 版本信息（补齐 K13 遗留绑定缺口：AboutPage/LogViewerPage 消费）
    single<top.mcxiafeng.badger.platform.AppInfo> { top.mcxiafeng.badger.BadgerAppInfo(androidContext()) }
    // [KMP K13c] Deep link 消费边界（common App composable 经契约消费，MainActivity 喂事件）
    single<top.mcxiafeng.badger.platform.AppLinkHandler> { DeepLinkBus }
}
