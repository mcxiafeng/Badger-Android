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

/**
 * [KMP K16] iOS 启动引导 + Compose 入口。
 *
 * **BGTask 注册时序**：`BGTaskScheduler.registerForTaskWithIdentifier` 必须在 app 结束
 * didFinishLaunching 前调用——Swift 壳（iOSApp.swift）在 `App.init()` 内同步调
 * `initializeIosApp()`；[MainViewController] 构造时再调一次（幂等守卫，兜底 SwiftUI 生命周期差异）。
 */
object IosAppBootstrap {

    private const val TAG = "IosAppBootstrap"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @kotlin.concurrent.Volatile
    private var initialized = false

    private fun koin() = KoinPlatformTools.defaultContext().get()

    fun initialize() {
        if (initialized) return
        initialized = true

        // [KMP K05] DataStore：iOS 无 SharedPreferences 迁移负担，直接灌内存快照
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

        // BGTask 注册（launch 窗口内；模拟器 submit 恒失败只记日志）
        koin().get<SyncDispatcher>().registerBackgroundTask()

        // 异步预加载行政区划数据（80KB + 700KB），后台跑不阻塞首屏——对齐 BadgerApplication
        appScope.launch {
            try {
                koin().get<WorldRegionRepository>().loadCountries()
                BadgerLog.d(TAG, "预加载 countries.json 完成")
                koin().get<LegacyTagFixup>().runOnce()
            } catch (e: Exception) {
                BadgerLog.w(TAG, "后台启动副作用失败(可忽略)", e)
            }
        }

        // 启动完整同步：iOS 全新库经 pull bootstrap 收敛（Q4 裁决），未登录 401 内部降级
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

/** Swift 侧入口（IosAppBootstrapKt.initializeIosApp()）：App.init() 内调用，保 BGTask 注册时序。 */
fun initializeIosApp() = IosAppBootstrap.initialize()

/**
 * iOS Compose 宿主（SwiftUI 经 UIViewControllerRepresentable 持有）。
 * 键盘避让：Compose 自处理（Swift 壳 `.ignoresSafeArea(.keyboard)`，见 iosApp/ContentView.swift）。
 */
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
