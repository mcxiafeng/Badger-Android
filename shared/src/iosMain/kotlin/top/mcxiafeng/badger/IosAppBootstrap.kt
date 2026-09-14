package top.mcxiafeng.badger

import androidx.compose.ui.window.ComposeUIViewController
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController
import top.mcxiafeng.badger.data.LegacyTagFixup
import top.mcxiafeng.badger.data.prefs.PrefsStore
import top.mcxiafeng.badger.data.repository.WorldRegionRepository
import top.mcxiafeng.badger.di.iosAppStateModule
import top.mcxiafeng.badger.di.iosDatabaseModule
import top.mcxiafeng.badger.di.iosImageLoader
import top.mcxiafeng.badger.di.iosNetworkModule
import top.mcxiafeng.badger.di.iosRepositoryModule
import top.mcxiafeng.badger.di.useCaseModule
import top.mcxiafeng.badger.di.viewModelModule
import top.mcxiafeng.badger.sync.SyncDispatcher
import top.mcxiafeng.badger.sync.SyncEngine
import top.mcxiafeng.badger.ui.navigation.NavBarConfig
import top.mcxiafeng.badger.ui.navigation.ThemeConfig
import top.mcxiafeng.badger.utils.BadgerLog

object IosAppBootstrap {

    private const val TAG = "IosAppBootstrap"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @kotlin.concurrent.Volatile
    private var initialized = false

    private fun koin() = KoinPlatformTools.defaultContext().get()

    fun initialize() {
        if (initialized) return
        initialized = true

        
        PrefsStore.initialize()
        NavBarConfig.initialize()
        ThemeConfig.initialize()

        runCatching { KoinPlatformTools.defaultContext().get() }.getOrNull()?.close()
        startKoin {
            modules(
                iosDatabaseModule,
                iosRepositoryModule,
                iosNetworkModule,
                useCaseModule,
                iosAppStateModule,
                viewModelModule,
            )
        }
        BadgerLog.d(TAG, "Koin 容器启动完成（iOS 模块集）")

        
        koin().get<SyncDispatcher>().registerBackgroundTask()

        
        appScope.launch {
            try {
                koin().get<WorldRegionRepository>().loadCountries()
                BadgerLog.d(TAG, "预加载 countries.json 完成")
                koin().get<LegacyTagFixup>().runOnce()
            } catch (e: Exception) {
                BadgerLog.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        
        appScope.launch {
            try {
                val result = koin().get<SyncEngine>().syncOnceIfIdle()
                BadgerLog.d(TAG, "启动同步完成: $result")
            } catch (e: Exception) {
                BadgerLog.w(TAG, "启动同步失败(可忽略,下次启动重试)", e)
            }
        }
    }
}

fun initializeIosApp() = IosAppBootstrap.initialize()

@Suppress("unused")
fun MainViewController(): UIViewController = run {
    IosAppBootstrap.initialize()
    ComposeUIViewController {
        setSingletonImageLoaderFactory { context: PlatformContext ->
            iosImageLoader(context)
        }
        AppTheme { App() }
    }
}
