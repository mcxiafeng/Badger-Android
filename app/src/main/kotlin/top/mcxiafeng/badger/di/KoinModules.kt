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

val databaseModule = module {
    includes(daoModule)

    single {
        AppDatabaseHost.build(get())
    }
}

val repositoryModule = commonRepositoryModule(::downloadAndSaveAvatar)

val networkModule = module {
    single { ServerApiFactory() }
    single { NetworkModule.provideTokenHolder() }
    
    single {
        NetworkModule.provideOkHttpClient(
            context = androidContext(),
            tokenHolder = get(),
        )
    }
    
    
    
    
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
    
    singleOf(::ContactNetworkResolver)
    singleOf(::ShortLinkService)
}

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

val appStateModule = module {
    includes(commonAppStateModule)
    
    singleOf(::OutboxScheduler)
    
    single { top.mcxiafeng.badger.platform.NfcWriter() }
    
    single<top.mcxiafeng.badger.platform.AppInfo> { top.mcxiafeng.badger.BadgerAppInfo(androidContext()) }
    
    single<top.mcxiafeng.badger.platform.AppLinkHandler> { DeepLinkBus }
}
